package jp.linkserver.shutter;

interface IShutterService {
    // Shizuku calls this transaction to stop the user service.
    void destroy() = 16777114;

    int getForSystem() = 1;
    void setForSystem(int config) = 2;

    String getGalaxyShutter(int userId) = 3;
    // False means the command timed out and its write may still be pending.
    boolean setGalaxyShutter(int userId, int value) = 4;
}
