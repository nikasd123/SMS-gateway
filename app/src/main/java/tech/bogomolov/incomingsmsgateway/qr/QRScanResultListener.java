package tech.bogomolov.incomingsmsgateway.qr;

public interface QRScanResultListener {
    void onQRScanResult(String qrData);
}
