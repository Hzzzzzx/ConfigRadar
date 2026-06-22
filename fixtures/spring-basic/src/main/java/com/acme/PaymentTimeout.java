package com.acme;

import org.springframework.beans.factory.annotation.Value;

@Value("${payment.client.timeout}")
public @interface PaymentTimeout {
}
