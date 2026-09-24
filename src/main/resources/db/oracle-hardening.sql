-- Re-runnable Oracle procedure hardening applied automatically at startup.
CREATE OR REPLACE EDITIONABLE PROCEDURE "SP_ADD_PURCHASE_ITEM" (
    P_PURCHASE_ID IN NUMBER,
    P_VARIANT_ID  IN NUMBER,
    P_QUANTITY    IN NUMBER,
    P_COST_PRICE  IN NUMBER
)
AS
    V_STATUS VARCHAR2(20);
    V_COUNT NUMBER;
BEGIN

    IF P_QUANTITY <= 0 THEN
        RAISE_APPLICATION_ERROR(
            -20101,
            'Purchase quantity must be greater than zero.'
        );
    END IF;


    IF P_COST_PRICE < 0 THEN
        RAISE_APPLICATION_ERROR(
            -20102,
            'Cost price cannot be negative.'
        );
    END IF;


    BEGIN

        SELECT STATUS
        INTO V_STATUS
        FROM PURCHASE
        WHERE PURCHASE_ID = P_PURCHASE_ID
        FOR UPDATE;

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(
                -20103,
                'Purchase does not exist.'
            );
    END;


    IF V_STATUS <> 'PENDING' THEN
        RAISE_APPLICATION_ERROR(
            -20104,
            'Only pending purchases can be edited.'
        );
    END IF;


    SELECT COUNT(*)
    INTO V_COUNT
    FROM PURCHASE_DETAIL
    WHERE PURCHASE_ID = P_PURCHASE_ID
      AND VARIANT_ID = P_VARIANT_ID;


    IF V_COUNT > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20108,
            'The same product variant cannot appear more than once in a purchase.'
        );
    END IF;


    INSERT INTO PURCHASE_DETAIL (
        PURCHASE_ID,
        VARIANT_ID,
        QUANTITY,
        COST_PRICE,
        SUBTOTAL
    )
    VALUES (
        P_PURCHASE_ID,
        P_VARIANT_ID,
        P_QUANTITY,
        P_COST_PRICE,
        P_QUANTITY * P_COST_PRICE
    );

END;
/

  CREATE OR REPLACE EDITIONABLE PROCEDURE "SP_COMPLETE_PURCHASE" (
    P_PURCHASE_ID IN NUMBER
)
AS
    V_STATUS VARCHAR2(20);
    V_COUNT NUMBER;
    V_TOTAL NUMBER;
BEGIN

    BEGIN

        SELECT STATUS
        INTO V_STATUS
        FROM PURCHASE
        WHERE PURCHASE_ID = P_PURCHASE_ID
        FOR UPDATE;

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(
                -20105,
                'Purchase does not exist.'
            );
    END;


    IF V_STATUS <> 'PENDING' THEN
        RAISE_APPLICATION_ERROR(
            -20106,
            'Purchase is not pending.'
        );
    END IF;


    SELECT COUNT(*)
    INTO V_COUNT
    FROM PURCHASE_DETAIL
    WHERE PURCHASE_ID = P_PURCHASE_ID;


    IF V_COUNT = 0 THEN
        RAISE_APPLICATION_ERROR(
            -20107,
            'Purchase has no items.'
        );
    END IF;


    SELECT SUM(SUBTOTAL)
    INTO V_TOTAL
    FROM PURCHASE_DETAIL
    WHERE PURCHASE_ID = P_PURCHASE_ID;


    FOR R IN (
        SELECT
            VARIANT_ID,
            QUANTITY,
            COST_PRICE
        FROM PURCHASE_DETAIL
        WHERE PURCHASE_ID = P_PURCHASE_ID
    )
    LOOP

        PKG_INVENTORY.ADD_STOCK(
            P_VARIANT_ID     => R.VARIANT_ID,
            P_QUANTITY       => R.QUANTITY,
            P_REFERENCE_TYPE => 'PURCHASE',
            P_REFERENCE_ID   => P_PURCHASE_ID,
            P_NOTE           => 'Purchase completed'
        );


        UPDATE PRODUCT_VARIANT
        SET COST_PRICE = R.COST_PRICE
        WHERE VARIANT_ID = R.VARIANT_ID;

    END LOOP;


    UPDATE PURCHASE
    SET
        TOTAL_AMOUNT = V_TOTAL,
        STATUS = 'COMPLETED'
    WHERE PURCHASE_ID = P_PURCHASE_ID;

END;
/

  CREATE OR REPLACE EDITIONABLE PROCEDURE "SP_ADD_SALE_ITEM" (
    P_SALE_ID       IN NUMBER,
    P_VARIANT_ID    IN NUMBER,
    P_QUANTITY      IN NUMBER,
    P_UNIT_PRICE    IN NUMBER DEFAULT NULL,
    P_LINE_DISCOUNT IN NUMBER DEFAULT 0
)
AS
    V_STATUS VARCHAR2(20);
    V_PRICE NUMBER;
    V_GROSS NUMBER;
    V_COUNT NUMBER;
BEGIN

    IF P_QUANTITY <= 0 THEN
        RAISE_APPLICATION_ERROR(
            -20202,
            'Sale quantity must be greater than zero.'
        );
    END IF;


    IF P_LINE_DISCOUNT < 0 THEN
        RAISE_APPLICATION_ERROR(
            -20203,
            'Line discount cannot be negative.'
        );
    END IF;


    BEGIN

        SELECT STATUS
        INTO V_STATUS
        FROM SALE
        WHERE SALE_ID = P_SALE_ID
        FOR UPDATE;

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(
                -20204,
                'Sale does not exist.'
            );
    END;


    IF V_STATUS <> 'PENDING' THEN
        RAISE_APPLICATION_ERROR(
            -20205,
            'Only pending sales can be edited.'
        );
    END IF;


    BEGIN

        SELECT SALE_PRICE
        INTO V_PRICE
        FROM PRODUCT_VARIANT
        WHERE VARIANT_ID = P_VARIANT_ID;

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(
                -20206,
                'Product variant does not exist.'
            );
    END;


    IF P_UNIT_PRICE IS NOT NULL AND P_UNIT_PRICE <> V_PRICE THEN
        RAISE_APPLICATION_ERROR(
            -20213,
            'Submitted price does not match the current catalog price.'
        );
    END IF;


    IF V_PRICE < 0 THEN
        RAISE_APPLICATION_ERROR(
            -20207,
            'Unit price cannot be negative.'
        );
    END IF;


    V_GROSS := P_QUANTITY * V_PRICE;


    IF P_LINE_DISCOUNT > V_GROSS THEN
        RAISE_APPLICATION_ERROR(
            -20208,
            'Line discount cannot exceed line amount.'
        );
    END IF;


    SELECT COUNT(*)
    INTO V_COUNT
    FROM SALE_DETAIL
    WHERE SALE_ID = P_SALE_ID
      AND VARIANT_ID = P_VARIANT_ID;


    IF V_COUNT > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20214,
            'The same product variant cannot appear more than once in a sale.'
        );
    END IF;


    INSERT INTO SALE_DETAIL (
        SALE_ID,
        VARIANT_ID,
        QUANTITY,
        UNIT_PRICE,
        DISCOUNT,
        SUBTOTAL
    )
    VALUES (
        P_SALE_ID,
        P_VARIANT_ID,
        P_QUANTITY,
        V_PRICE,
        P_LINE_DISCOUNT,
        V_GROSS - P_LINE_DISCOUNT
    );

END;
/

  CREATE OR REPLACE EDITIONABLE PROCEDURE "SP_COMPLETE_SALE" (
    P_SALE_ID IN NUMBER
)
AS
    V_STATUS VARCHAR2(20);
    V_ORDER_DISCOUNT NUMBER;
    V_COUNT NUMBER;
    V_SUBTOTAL NUMBER;
    V_GRAND_TOTAL NUMBER;
BEGIN

    BEGIN

        SELECT
            STATUS,
            DISCOUNT
        INTO
            V_STATUS,
            V_ORDER_DISCOUNT
        FROM SALE
        WHERE SALE_ID = P_SALE_ID
        FOR UPDATE;

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(
                -20209,
                'Sale does not exist.'
            );
    END;


    IF V_STATUS <> 'PENDING' THEN
        RAISE_APPLICATION_ERROR(
            -20210,
            'Sale is not pending.'
        );
    END IF;


    SELECT COUNT(*)
    INTO V_COUNT
    FROM SALE_DETAIL
    WHERE SALE_ID = P_SALE_ID;


    IF V_COUNT = 0 THEN
        RAISE_APPLICATION_ERROR(
            -20211,
            'Sale has no items.'
        );
    END IF;


    SELECT SUM(SUBTOTAL)
    INTO V_SUBTOTAL
    FROM SALE_DETAIL
    WHERE SALE_ID = P_SALE_ID;


    IF V_ORDER_DISCOUNT > V_SUBTOTAL THEN
        RAISE_APPLICATION_ERROR(
            -20212,
            'Order discount exceeds subtotal.'
        );
    END IF;


    V_GRAND_TOTAL :=
        V_SUBTOTAL - V_ORDER_DISCOUNT;


    FOR R IN (
        SELECT
            VARIANT_ID,
            QUANTITY
        FROM SALE_DETAIL
        WHERE SALE_ID = P_SALE_ID
    )
    LOOP

        PKG_INVENTORY.REDUCE_STOCK(
            P_VARIANT_ID     => R.VARIANT_ID,
            P_QUANTITY       => R.QUANTITY,
            P_REFERENCE_TYPE => 'SALE',
            P_REFERENCE_ID   => P_SALE_ID,
            P_NOTE           => 'Sale completed'
        );

    END LOOP;


    UPDATE SALE
    SET
        SUBTOTAL = V_SUBTOTAL,
        GRAND_TOTAL = V_GRAND_TOTAL,
        STATUS = 'COMPLETED'
    WHERE SALE_ID = P_SALE_ID;

END;
/

  CREATE OR REPLACE EDITIONABLE PROCEDURE "SP_RECORD_PAYMENT" (
    P_SALE_ID        IN NUMBER,
    P_AMOUNT         IN NUMBER,
    P_PAYMENT_METHOD IN VARCHAR2,
    P_REFERENCE_NO   IN VARCHAR2 DEFAULT NULL
)
AS
    V_STATUS VARCHAR2(20);
    V_TOTAL NUMBER;
    V_PAID NUMBER;
BEGIN

    IF P_AMOUNT <= 0 THEN
        RAISE_APPLICATION_ERROR(
            -20301,
            'Payment amount must be greater than zero.'
        );
    END IF;


    BEGIN

        SELECT
            STATUS,
            GRAND_TOTAL
        INTO
            V_STATUS,
            V_TOTAL
        FROM SALE
        WHERE SALE_ID = P_SALE_ID
        FOR UPDATE;

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(
                -20302,
                'Sale does not exist.'
            );
    END;


    IF V_STATUS <> 'COMPLETED' THEN
        RAISE_APPLICATION_ERROR(
            -20303,
            'Only completed sales can receive payment.'
        );
    END IF;


    SELECT NVL(SUM(AMOUNT), 0)
    INTO V_PAID
    FROM PAYMENT
    WHERE SALE_ID = P_SALE_ID
    AND PAYMENT_STATUS = 'PAID';


    IF V_PAID + P_AMOUNT > V_TOTAL THEN
        RAISE_APPLICATION_ERROR(
            -20304,
            'Payment exceeds sale balance.'
        );
    END IF;


    INSERT INTO PAYMENT (
        SALE_ID,
        AMOUNT,
        PAYMENT_METHOD,
        PAYMENT_STATUS,
        REFERENCE_NO
    )
    VALUES (
        P_SALE_ID,
        P_AMOUNT,
        UPPER(P_PAYMENT_METHOD),
        'PAID',
        P_REFERENCE_NO
    );

END;
/

CREATE OR REPLACE FORCE EDITIONABLE VIEW "V_WEEKLY_SALES" ("WEEK_START", "WEEK_END", "TOTAL_SALES", "SUBTOTAL", "ORDER_DISCOUNT", "NET_SALES") AS 
SELECT
    TO_CHAR(TRUNC(SALE_DATE, 'IW'), 'YYYY-MM-DD') AS WEEK_START,
    TO_CHAR(TRUNC(SALE_DATE, 'IW') + 6, 'YYYY-MM-DD') AS WEEK_END,
    COUNT(*) AS TOTAL_SALES,
    SUM(SUBTOTAL) AS SUBTOTAL,
    SUM(DISCOUNT) AS ORDER_DISCOUNT,
    SUM(GRAND_TOTAL) AS NET_SALES
FROM SALE
WHERE STATUS = 'COMPLETED'
GROUP BY TRUNC(SALE_DATE, 'IW');
/

CREATE OR REPLACE FORCE EDITIONABLE VIEW "V_YEARLY_SALES" ("SALE_YEAR", "TOTAL_SALES", "SUBTOTAL", "ORDER_DISCOUNT", "NET_SALES") AS 
SELECT
    TO_CHAR(SALE_DATE, 'YYYY') AS SALE_YEAR,
    COUNT(*) AS TOTAL_SALES,
    SUM(SUBTOTAL) AS SUBTOTAL,
    SUM(DISCOUNT) AS ORDER_DISCOUNT,
    SUM(GRAND_TOTAL) AS NET_SALES
FROM SALE
WHERE STATUS = 'COMPLETED'
GROUP BY TO_CHAR(SALE_DATE, 'YYYY');
/

