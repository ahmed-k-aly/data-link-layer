import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ParityDataLinkLayer extends DataLinkLayer{
    // The start tag, stop tag, and the escape tag.
    private static final byte startTag  = (byte)'{';
    private static final byte stopTag   = (byte)'}';
    private static final byte escapeTag = (byte)'\\';

    @Override
    protected byte[] createFrame(byte[] data) {
        // TODO Auto-generated method stub
        Queue<Byte> framingData = new LinkedList<Byte>(); // Create queue of frames
        
        framingData.add(startTag); // add start tag
        int numOnesInFrame = 0; // keeps track of number of ones in the frame

        for (int i = 0; i < data.length; i++) {
            numOnesInFrame += Integer.bitCount(data[i] & 0xff);

            if (data[i] == startTag || data[i] == escapeTag || data[i] == stopTag) { // check if we need an escape tag
                framingData.add(escapeTag); // add escape tag
            }

            framingData.add(data[i]); // add data to the frame

            if ((i+1)%8 == 0){ // ensure every frame is only 8 bytes long
                byte parityByte = (byte) (numOnesInFrame % 2); // find parity
                framingData.add(stopTag); // add stop tag
                framingData.add(parityByte); // add parity byte
                framingData.add(startTag);
            }
        }

        // convert framed data queue to byte array
        byte[] framedData = new byte[framingData.size()];
        Iterator<Byte>  i = framingData.iterator();
        int             j = 0;
        while (i.hasNext()) {
            framedData[j++] = i.next();
        }
        return framedData;
    }

    @Override
    protected byte[] processFrame () {
        Logger logger = Logger.getLogger("ProcessFrame");

        // Search for a start tag.  Discard anything prior to it.
        boolean        startTagFound = false;
        Iterator<Byte>             i = byteBuffer.iterator();
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
            logger.log(Level.SEVERE, "no start tag found");
            return null;
        }
        int numOnesInFrame = 0;
        // Try to extract data while waiting for an unescaped stop tag.
        Queue<Byte> extractedBytes = new LinkedList<Byte>();
        boolean       stopTagFound = false;
        int byteCount = 0; // keeps track of the number of bytes that have been processed
        while (!stopTagFound && i.hasNext()) {
    
            // Grab the next byte.  If it is...
            //   (a) An escape tag: Skip over it and grab what follows as
            //                      literal data.
            //   (b) A stop tag:    Remove all processed bytes from the buffer and
            //                      end extraction.
            //   (c) A start tag:   All that precedes is damaged, so remove it
            //                      from the buffer and restart extraction.
            //   (d) Otherwise:     Take it as literal data.
            byte current = i.next();
            if (current == escapeTag) {
            if (i.hasNext()) {
                current = i.next();
                if (byteCount > 8 && current != stopTag) { // checks that frame is only 8 bytes long
                    logger.log(Level.SEVERE, "Frame more than 8 bytes"); // log error
                    return null; // return null
                }
                extractedBytes.add(current);
                numOnesInFrame += Integer.bitCount(current & 0xff);
                byteCount++; // increment byte counter.

            } else {
                // An escape was the last byte available, so this is not a
                // complete frame.
                logger.log(Level.SEVERE, "Incomplete Frame");
                return null;
            }
            } else if (current == stopTag) {
            cleanBufferUpTo(i);
            stopTagFound = true;
            } else if (current == startTag) {
            cleanBufferUpTo(i);
            extractedBytes = new LinkedList<Byte>();
            } else {
                
            if (byteCount > 8 && current != stopTag) { // checks that frame is only 8 bytes long
                logger.log(Level.SEVERE, "Frame more than 8 bytes"); // log error
                return null; // return null
            }
            extractedBytes.add(current);
            numOnesInFrame += Integer.bitCount(current & 0xff);
            byteCount++; // increment byte counter
            }
    
        }
    
        // If there is no stop tag, then the frame is incomplete.
        if (!stopTagFound) {
            logger.log(Level.SEVERE, "no stop tag found");
            return null;
        }

        if (stopTagFound) { // if there is a stop tag, we get the parityByte.
            byte parityByte = i.next();
            byte calculatedParityByte = (byte) (numOnesInFrame %2);
            if (parityByte != calculatedParityByte) { // compare parity bits and ensure they work
                logger.log(Level.SEVERE, "parity bits mismatch");
                return null;
            }
        }
    
        // Convert to the desired byte array.
        if (debug) {
            System.out.println("DumbDataLinkLayer.processFrame(): Got whole frame!");
        }
        byte[] extractedData = new byte[extractedBytes.size()];
        int                j = 0;
        i = extractedBytes.iterator();
        while (i.hasNext()) {
            extractedData[j] = i.next();
            if (debug) {
            System.out.printf("DumbDataLinkLayer.processFrame():\tbyte[%d] = %c\n",
                      j,
                      extractedData[j]);
            }
            j += 1;
        }
    
        return extractedData;
    
        } // processFrame ()
        // ===============================================================
    
    
    
        // ===============================================================
        private void cleanBufferUpTo (Iterator<Byte> end) {
    
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