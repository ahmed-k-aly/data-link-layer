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
    private static final boolean logging = true;
    
    /**
     * @param data
     * @return byte[]
     */
    @Override
    protected byte[] createFrame(byte[] data) {
        // TODO Auto-generated method stub
        Queue<Byte> framingData = new LinkedList<Byte>(); // Create queue of frames
        Queue<Byte> onlyOneFrameQueue = new LinkedList<Byte>();// Create queue full of only the main data for each
                                                               // frame.
        framingData.add(startTag); // add start tag
        for (int i = 0; i < data.length; i++) {
            if (data[i] == startTag || data[i] == escapeTag || data[i] == stopTag) { // check if we need an escape tag
                framingData.add(escapeTag); // add escape tag
            }
            onlyOneFrameQueue.add(data[i]); // add data to the one frame queue
            framingData.add(data[i]); // add data to the frame

            if ((i + 1) % 8 == 0) { // ensure every frame is only 8 bytes long
                byte remainder = otherCRC(onlyOneFrameQueue); // compute crc.
                onlyOneFrameQueue.clear(); // clear the queue after the frame is done.
                if (remainder == startTag || remainder == escapeTag || remainder == stopTag) { // check if we need an escape tag
                    framingData.add(escapeTag); // add escape tag
                }
                framingData.add(remainder); // add remainder byte
                framingData.add(stopTag); // add stop tag
                framingData.add(startTag); // add start tag
            }
        }

        if (framingData.size() != 0) { // always true
            byte remainder = otherCRC(onlyOneFrameQueue); // compute crc.
            if (remainder == startTag || remainder == escapeTag || remainder == stopTag) { // check if we need an escape tag
                framingData.add(escapeTag); // add escape tag
            }
            framingData.add(remainder);
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

    private byte otherCRC (Queue<Byte> byteQueue) {

        int generator = 0x1d5;
        int remainder = 0;
        Iterator<Byte> iterator = byteQueue.iterator();
        while (iterator.hasNext()) {
            byte currByte = iterator.next();
            for (int i = 0; i < 8; i++) { // Iterate through the byte, get all the bits, and keep performing the operation.
                
                // This adds one bit to the integer buffer -- the bit that is coming from the array of bytes.
                remainder = (remainder << 1) | ((currByte >> (7 - i)) & 1);
                if (((remainder >> 8) & 1) == 1) {
                    // If our integer buffer has a 1 in the ninth spot, do the operation.
                    remainder ^= generator;
                }

            }
        }
        return (byte)remainder;
    }

    /**
     * Is a method that shifts everything in the passed queue to the left.
     * 
     * @param dividend      is the dividend we are using to calculate the crc in the
     *                      current cycle.
     * @param byteQueueCopy is the byte queue we store all our bytes in.
     * @param k             is the number of bits to shift
     * @return Queue<Byte> is the shifted queue
     */
    
    /**
     * @return byte[]
     */
    @Override
    protected byte[] processFrame() {
        Logger logger = Logger.getLogger("ProcessFrame"); // initialize logger
        if (!logging) { // checks if logging is not enabled for error messages. Logging is a private
                        // final variable for the class
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
            byte[] extractedBytesArr = new byte[extractedBytes.size()-1]; // create an array that's one less in length
            Iterator<Byte> iter = extractedBytes.iterator(); // iterate through extracted bytes
            for (int j = 0; j < extractedBytesArr.length; j++) { // fill array
                extractedBytesArr[j] = iter.next();
            }  
            byte crcByte = iter.next(); // get the crc byte
            extractedBytes.clear(); // clear the extracted bytes queue
            for (int j = 0; j < extractedBytesArr.length; j++){ // create the queue of extracted bytes again without the CRC byte.
                extractedBytes.add(extractedBytesArr[j]);
            }
                int remainder = otherCRC(extractedBytes); // compute crc.
                if (crcByte != remainder) {
                    // check that crc is zero
                    logger.log(Level.SEVERE, "CRC NOT EQUAL TO ZERO;\nFrame: "
                            + new String(extractedBytesArr));
                    return null;
                }
            }
        
        //}
        // If there is no stop tag, then the frame is incomplete.
        else {
            logger.log(Level.FINE, "NO STOP TAG FOUND");
            return null;
        }

        // Convert to the desired byte array.
        if (debug) {
            System.out.println("DumbDataLinkLayer.processFrame(): Got whole frame!");
        }
        byte[] extractedData = new byte[extractedBytes.size()];
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