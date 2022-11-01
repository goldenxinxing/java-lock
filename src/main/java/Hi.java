public class Hi {
    public static void main(String[] args) {
        // v=0.0
        var v = Double.longBitsToDouble(Long.parseUnsignedLong((String) "0000000000000000", 16));
        var v2 = Double.longBitsToDouble(Long.parseUnsignedLong((String) "0000000111111111", 16));
        var str = Long.toHexString(Double.doubleToLongBits(v2));
        var str2 = Long.toHexString(Double.doubleToLongBits(0.0));
        System.out.println(str + "," + str2);
        Integer a1 = 127;
        Integer a2 = 127;
        System.out.println(a1==a2);
    }
}
