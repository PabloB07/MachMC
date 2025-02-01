package cl.xgamers.machMC.payments;


import lombok.Data;

@Data
public class PaymentNotification {
    private String metadata;
    private String status;
    private String vipRank;
    private double amount;
}