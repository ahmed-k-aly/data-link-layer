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
        int ones = 0;
        for (int i = 0; i < data.length; i++) {
            ones += Integer.bitCount(data[i] & 0xff);
            if (data[i] == startTag || data[i] == escapeTag || data[i] == stopTag) { // check if we need an escape tag
                framingData.add(escapeTag);
            }
            framingData.add(data[i]); // add data to the frame
            if ((i+1)%8 == 0){ // ensure every frame is only 8 bytes long
                byte parityByte = (byte) (ones % 2); // find parity
                framingData.add(parityByte); // add parity byte
                framingData.add(stopTag); // add stop tag
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
    protected byte[] processFrame() {

        Logger logger = Logger.getLogger("ParityDataLinkLayer");
        Queue<Byte> cleanDataToReturn = new LinkedList<Byte>();
        Byte[] framedData = null;
        framedData = byteBuffer.toArray(new Byte[byteBuffer.size()]); // convert byte buffer to byte array
        
        if (framedData.length < 4){
            // Log error
            System.err.println("Data Error");
            logger.log(Level.SEVERE, "Data Error");
            return null;
        }

        for (int i = 0; i < framedData.length - 4; i+= 4){
            boolean escapeFlag = false;
            Byte firstQuarter = framedData[i];
            if (firstQuarter != startTag){// check if first part of the message is start tag
                return null;
            } 
            Byte secondQuarter = framedData[i + 1];
            if (secondQuarter == escapeTag){ // check if secondQuarter is an escape tag
                i+=1; // skip that part of the data
                escapeFlag = true;
            }
            secondQuarter = framedData[i + 1];
            if (!escapeFlag  && ((secondQuarter == startTag) || (secondQuarter == stopTag))){ // tests if data is dropped or metadata is messed up
                System.err.println("Data Error");
                logger.log(Level.SEVERE, "Data Error");
                return null;
            }
            Byte thirdQuarter = framedData[i + 2];
            int numOnesForParityCheck = Integer.bitCount(secondQuarter & 0xff); // check parity of the parity check
            byte parity = (byte)(numOnesForParityCheck % 2);


        }
        return null;
    }
    
}