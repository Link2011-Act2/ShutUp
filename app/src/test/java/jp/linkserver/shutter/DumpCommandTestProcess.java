package jp.linkserver.shutter;

/** Child process used to exercise actual OS pipes, exits and termination. */
public final class DumpCommandTestProcess {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("slow")) {
            Thread.sleep(60000);
        } else if (args[0].equals("fail")) {
            System.out.print("SYSTEM SETTINGS (user 0)\n");
            System.exit(7);
        } else {
            byte[] chunk = new byte[8192];
            java.util.Arrays.fill(chunk, (byte) 'x');
            for (int i = 0; i < 256; i++) System.out.write(chunk);
            for (int i = 0; i < 32; i++) System.err.write(chunk);
            System.out.flush();
            System.err.flush();
        }
    }
}
