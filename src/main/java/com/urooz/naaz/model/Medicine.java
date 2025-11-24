package com.urooz.naaz.model;

public record Medicine(
        String itemId,
        String medicineName,
        String genericName,
        String rackLocation,
        String stockQuantity,
        String price,
        String expiryDate,
        String usageDescription
) {}