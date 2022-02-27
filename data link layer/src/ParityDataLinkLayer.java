import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ParityDataLinkLayer extends DataLinkLayer {
    // The start tag, stop tag, and the escape tag.
    private static final byte startTag = (byte) '{';
    private static final byte stopTag = (byte) '}';
    private static final byte escapeTag = (byte) '\\';
    private static final boolean logging = false;
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
                numOnesInFrame -= Integer.bitCount(data[i] & 0xff); // don't include escape tag in parity.
            }

            framingData.add(data[i]); // add data to the frame

            if ((i + 1) % 8 == 0) { // ensure every frame is only 8 bytes long
                byte parityByte = (byte) (numOnesInFrame % 2); // find parity
                framingData.add(parityByte); // add parity byte
                framingData.add(stopTag); // add stop tag
                framingData.add(startTag); // add start tag
                numOnesInFrame = 0;

            }
        }

        if (framingData.size() != 0) {
            framingData.add((byte) (numOnesInFrame % 2)); // add parity byte

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

    @Override
    protected byte[] processFrame() {

        Logger logger = Logger.getLogger("ProcessFrame");
        if (!logging){
            logger.setLevel(Level.OFF);
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
            logger.log(Level.WARNING, "NO START TAG FOUND. NO FRAME FOUND");
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
                        logger.log(Level.SEVERE, "FRAME GREATER THAN 8 BYTES"); // log error
                        return null; // return null
                    }
                    extractedBytes.add(current); // add currentByte to extractedBytes
                    byteCount++; // increment byte counter.

                } else {
                    // An escape was the last byte available, so this is not a
                    // complete frame.
                    logger.log(Level.SEVERE, "INCOMPLETE FRAME");
                    return null;
                }
            } else if (current == stopTag) {
                cleanBufferUpTo(i);
                stopTagFound = true;
            } else if (current == startTag) {
                // data is messed up
                logger.log(Level.SEVERE, "NO STOP TAG FOUND, DATA MESSED UP, DROPPING FRAME.");
                cleanBufferUpTo(i);
                extractedBytes = new LinkedList<Byte>();
            } else {
                if (byteCount > 8 && current != stopTag) { // checks that frame is only 8 bytes long
                    logger.log(Level.SEVERE, "FRAME MORE THAN 8 BYTES"); // log error
                    return null; // return null
                }
                extractedBytes.add(current); // add data to extracted bytes
                byteCount++; // increment byte counter
            }

        }
        if (stopTagFound) {
            byte[] extractedBytesArr = new byte[extractedBytes.size() - 1]; // create an array that's one less in length  than extracted bytes
            Iterator<Byte> iter = extractedBytes.iterator(); // iterate through extracted bytes
            int countNumOnesInFrame = 0;
            for (int j = 0; j < extractedBytesArr.length; j++) { // fill array
                extractedBytesArr[j] = iter.next();
                countNumOnesInFrame += Integer.bitCount(extractedBytesArr[j] & 0xff);
            }

            byte parityByte = iter.next(); // parityByte would be the only remaining byte
            byte calculatedParityByte = (byte) (countNumOnesInFrame % 2); // the parity byte calculated from the received
                                                                     // data

            if (calculatedParityByte != parityByte) {
                // check that parities match
                // logger.log(Level.SEVERE, "Calculated Parity: " + (int)calculatedParityByte + " Parity in Frame: " + (int)parityByte);
                logger.log(Level.SEVERE, "PARITIES MISMATCH");
                return null;
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

    } // processFrame ()
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