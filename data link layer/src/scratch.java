import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class scratch {
    
    public static void main(String[] args) {
        Queue<Byte> queue = new LinkedList<Byte>();
        queue.add((byte)5);
        byte result = otherCRC(queue);
        System.out.printf("Result: %d", result);
    }

    private static byte otherCRC (Queue<Byte> byteQueue) {

        int generator = 0x1D5;
        int buffer = 0;
        Iterator<Byte> iterator = byteQueue.iterator();
        while (iterator.hasNext()) {
            byte currByte = iterator.next();
            // Iterate through the byte, get all the bits, and keep performing the operation.
            for (int i = 0; i < 8; i++) {

                // This adds one bit to the integer buffer -- the bit that is coming from the array of bytes.
                buffer = (buffer << 1) | ((currByte >> (7 - i)) & 1);

                // If our integer buffer has a 1 in the ninth spot, do the operation.
                if (((buffer >> 8) & 1) == 1) {
                    buffer ^= generator;
                }

            }
        }
        return (byte)buffer;
    }



}
