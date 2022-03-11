import java.math.BigInteger;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CRCDataLinkLayer extends DataLinkLayer {
    // The start tag, stop tag, and the escape tag.
    private static final byte startTag = (byte) '{';
    private static final byte stopTag = (byte) '}';
    private static final byte escapeTag = (byte) '\\';
    private static final boolean logging = false;

    
    /** 
     * @param data
     * @return byte[]
     */
    @Override
    protected byte[] createFrame(byte[] data) {
        // TODO Auto-generated method stub
        Queue<Byte> framingData = new LinkedList<Byte>(); // Create queue of frames
        Queue<Byte> onlyOneFrameQueue = new LinkedList<Byte>();// Create queue full of only the main data for each frame.
        framingData.add(startTag); // add start tag
        int numOnesInFrame = 0; // keeps track of number of ones in the frame
        for (int i = 0; i < data.length; i++) {
            numOnesInFrame += Integer.bitCount(data[i] & 0xff);
            if (data[i] == startTag || data[i] == escapeTag || data[i] == stopTag) { // check if we need an escape tag
                framingData.add(escapeTag); // add escape tag
                numOnesInFrame -= Integer.bitCount(data[i] & 0xff); // don't include escape tag in parity counting.
            }
            onlyOneFrameQueue.add(data[i]); // add data to the one frame queue
            framingData.add(data[i]); // add data to the frame

            if ((i + 1) % 8 == 0) { // ensure every frame is only 8 bytes long
                int remainder = crc(onlyOneFrameQueue); // compute crc.
                onlyOneFrameQueue.clear(); // clear the queue after the frame is done.
                framingData.add((byte)remainder); // add remainder byte
                framingData.add(stopTag); // add stop tag
                framingData.add(startTag); // add start tag
                

            }
        }

        if (framingData.size() != 0) { // always true
            int remainder = crc(onlyOneFrameQueue); // compute crc.
            framingData.add((byte)remainder);
            framingData.add(stopTag);
        }
        // convert framed data queue to byte array
        byte[] framedData = new byte[framingData.size()];
        Iterator<Byte> i = framingData.iterator();
        int j = 0;
        while (i.hasNext()) {
            framedData[j++] = i.next();
        }
        return framedData;
    }

    
    /** Method that computes the CRC of the passed in queue of bytes.
     * @param byteQueue queue of bytes to compute the CRC of.
     * @return int the remainder of the CRC computation.
     */
    private int crc(Queue<Byte> byteQueue){
        Queue<Byte> byteQueueCopy = byteQueue;
        int generator = 0x0f9; // 249. initialize CRC-8 generator to get 7 bit remainder.
        int generatorSize = 7; // track generator size. TODO: Write a method that automatically calculates numDigits in number
        int totalNumBits = (byteQueue.size()*8) + (generatorSize - 1);// how many bits we are working with
        int remainder = 0; // initialize remainder.
        int numShifts = 0; // How many shifts occurred
        while (totalNumBits - numShifts > 0) { // keep looping until we use all our bits.
            int dividend = remainder;
            // find where the leading 1 bit is
            int leadingOneindex = findLeadingOnePosition((byte) dividend, generatorSize); // the leading 1 bit in the remainder.
            while (leadingOneindex < generatorSize) { // if the generator is larger than our leading one, we need to shift to the left
                int k = generatorSize - leadingOneindex; // find out how many bits to shift to the left.
                byteQueueCopy = leftShiftBits(dividend, byteQueueCopy, k); // shifts all the bits in the byte queue k bits to the left.
                dividend = byteQueueCopy.remove();
                numShifts += k;
            }
            remainder = divide(generator,(byte) dividend);
        }
        remainder = remainder << 1; // shift 7-bit remainder to the left to add padding.
        return remainder;
    }



    
    /** Is a method that shifts everything in the passed queue to the left.
     * @param dividend is the dividend we are using to calculate the crc in the current cycle.
     * @param byteQueueCopy is the byte queue we store all our bytes in.
     * @param k is the number of bits to shift
     * @return Queue<Byte> is the shifted queue
     */
    private Queue<Byte> leftShiftBits(int dividend, Queue<Byte> byteQueueCopy, int k) {
        
        byte[] byteArr = new byte[byteQueueCopy.size() + 1]; // create new byte array`
        byteArr[0] = (byte)dividend; // dividend is the zeroth byte
        // convert byte into array
        Iterator<Byte> i = byteQueueCopy.iterator(); 
        int j = 0;
        while (i.hasNext()) {
            byteArr[j++] = i.next();
        }

        BigInteger bigInt = new BigInteger(byteArr); // convert byte array into big integer
        BigInteger shiftedInt = bigInt.shiftLeft(k); // shift big integer left
        byte [] shiftedArray = shiftedInt.toByteArray(); // convert big integer to byte array
        
        Queue<Byte> toReturnQueue = new LinkedList<Byte>(); ;
        for (byte b: shiftedArray){
            toReturnQueue.add(b);
        }
        return toReturnQueue;
        
    }


    /** Returns the position of the leading 1 bit in the data
     * @param dividend the byte that is passed in
     * @return int the position of the leading 1 bit in the data
     */
    private int findLeadingOnePosition(byte dividend, int generatorSize) {
        int x = 0; // the leading 1 bit
        int i = generatorSize - 1; // the largest index is the size of the generator. The -1 is to adjust for starting at the zeroth index.
        while (x!= 1 && i>=0){ // while the bit is not 1 and the position counter is within the byte
            x = getBit(dividend, i);
            i--;
        }
        return i;
    }

    
    /** Method that XORs two numbers to divide them together in our field.
     * @param generator
     * @param dividend
     * @return int
     */
    private int divide(int generator, byte dividend){
        return generator ^ (int)dividend;
    }
    /**
     * Gets bit k from byte x,
     **/ 
    private static int getBit(int x, int k){
        return (x>>k)&1;
    }

    
    /** Sets bit at position k in byte x
     * @param x byte being changed
     * @param k position bit is being set in
     * @return int
     */
    private static int setBit(int x, int k){
        return(1<<k)|x;
    }

    
    /** 
     * @return byte[]
     */
    @Override
    protected byte[] processFrame() {

        Logger logger = Logger.getLogger("ProcessFrame"); // initialize logger
        if (!logging){ // checks if logging is not enabled for error messages. Logging is a private final variable for the class
            logger.setLevel(Level.OFF); // turns off logging if so.
        }
        // Search for a start tag. Discard anything prior to it.
        boolean startTagFound = false;
        Iterator<Byte> i = byteBuffer.iterator();
        while (!startTagFound && i.hasNext()) {
            byte current = i.next();
            if (current != startTag) {
                i.remove();
            } else {
                startTagFound = true;
            }
        }

        // If there is no start tag, then there is no frame.
        if (!startTagFound) {
            logger.log(Level.WARNING, "NO START TAG FOUND. NO FRAME FOUND"); // log error message
            return null;
        }
        // Try to extract data while waiting for an unescaped stop tag.
        Queue<Byte> extractedBytes = new LinkedList<Byte>();
        boolean stopTagFound = false;
        int byteCount = 0; // keeps track of the number of bytes that have been processed
        while (!stopTagFound && i.hasNext()) {

            // Grab the next byte. If it is...
            // (a) An escape tag: Skip over it and grab what follows as
            // literal data.
            // (b) A stop tag: Remove all processed bytes from the buffer and
            // end extraction.
            // (c) A start tag: All that precedes is damaged, so remove it
            // from the buffer and restart extraction.
            // (d) Otherwise: Take it as literal data.
            byte current = i.next();
            if (current == escapeTag) {
                if (i.hasNext()) {
                    current = i.next(); // current = the byte after the escape tag.
                    if (byteCount > 8) { // checks that frame is only 8 bytes long
                        logger.log(Level.SEVERE, "FRAME GREATER THAN 8 BYTES. DROPPING: " + current); // log error
                        return null; // return null
                    }
                    extractedBytes.add(current); // add currentByte to extractedBytes
                    byteCount++; // increment byte counter.
                } else {
                    return null; 
                }
            } else if (current == stopTag) {
                cleanBufferUpTo(i);
                stopTagFound = true;
            } else if (current == startTag) {
                // data is messed up
                logger.log(Level.SEVERE, "NO STOP TAG FOUND, DATA MESSED UP, DROPPING: " + current);
                cleanBufferUpTo(i);
                extractedBytes = new LinkedList<Byte>();
            } else {
                if (byteCount > 8 && current != stopTag) { // checks that frame is only 8 bytes long
                    logger.log(Level.SEVERE, "FRAME MORE THAN 8 BYTES. DROPPING: " + current); // log error
                    return null; // return null
                }
                extractedBytes.add(current); // add data to extracted bytes
                byteCount++; // increment byte counter
            }

        }
        if (stopTagFound) {
            byte[] extractedBytesArr = new byte[extractedBytes.size()]; // create an array that's one less in length  than extracted bytes
            Iterator<Byte> iter = extractedBytes.iterator(); // iterate through extracted bytes
            for (int j = 0; j < extractedBytesArr.length; j++) { // fill array
                extractedBytesArr[j] = iter.next();
            int remainder = crc(extractedBytes); // compute crc.
            
            if (remainder != 0) {
                // check that crc is zero
                logger.log(Level.SEVERE, "CRC NOT EQUAL TO ZERO; Remainder:" + remainder +"\nFrame: " + new String(extractedBytesArr));
                return null;
            }
        }

            
        }
        // If there is no stop tag, then the frame is incomplete.
        else {
            logger.log(Level.FINE, "NO STOP TAG FOUND");
            return null;
        }

        // Convert to the desired byte array.
        if (debug) {
            System.out.println("DumbDataLinkLayer.processFrame(): Got whole frame!");
        }
        byte[] extractedData = new byte[extractedBytes.size()-1];
        int j = 0;
        i = extractedBytes.iterator();
        while (i.hasNext() && j < extractedData.length) {
            extractedData[j] = i.next();
            if (debug) {
                System.out.printf("DumbDataLinkLayer.processFrame():\tbyte[%d] = %c\n",
                        j,
                        extractedData[j]);
            }
            j += 1;
        }

        return extractedData;

    } 
      // ===============================================================

    // ===============================================================
    private void cleanBufferUpTo(Iterator<Byte> end) {

        Iterator<Byte> i = byteBuffer.iterator();
        while (i.hasNext() && i != end) {
            i.next();
            i.remove();
        }

    }
    // ===============================================================

    // ===============================================================
    // DATA MEMBERS
    // ===============================================================

}