package com.clothing.app.service;

import com.clothing.app.dto.SaleRequestDto;
import com.clothing.app.entity.Payment;
import com.clothing.app.entity.Sale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class SaleCheckoutService {

    private final SaleService saleService;
    private final PaymentService paymentService;

    public SaleCheckoutService(SaleService saleService, PaymentService paymentService) {
        this.saleService = saleService;
        this.paymentService = paymentService;
    }

    @Transactional
    public CheckoutResult checkout(SaleRequestDto request) {
        Sale sale = saleService.createSale(request);
        Payment payment = null;
        if (request.getPaymentAmount() != null) {
            if (request.getPaymentAmount().signum() <= 0) {
                throw new IllegalArgumentException("Payment amount must be greater than zero");
            }
            payment = paymentService.recordPayment(
                    sale.getSaleId(), request.getPaymentAmount(),
                    request.getPaymentMethod(), request.getReferenceNo());
        }
        Map<String, BigDecimal> balance = paymentService.getBalance(sale.getSaleId());
        return new CheckoutResult(sale, payment, balance);
    }

    public record CheckoutResult(Sale sale, Payment payment, Map<String, BigDecimal> balance) {
    }
}
