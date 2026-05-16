package zhigalin.predictions.miniapp;

public class MiniAppException extends RuntimeException {

    private final int status;

    public MiniAppException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
