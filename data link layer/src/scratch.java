public class scratch {
    
    public static void main(String[] args) {
        int generator = 0x13;
        byte b  = (byte) 59;
        int v = generator^b;
        int x = 1<<4;
        byte res = (byte) ((byte)generator ^ b);
        System.out.printf("Int Representation %d",x);
    }




}
