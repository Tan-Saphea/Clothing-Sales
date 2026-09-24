# Clothing Sales Management System (CSMS)
## Comprehensive System Presentation & Defense Guide

---

## Table of Contents
1. [Executive Summary](#1-executive-summary)
2. [Project Objectives](#2-project-objectives)
3. [Problems in Traditional Retail & How CSMS Solves Them](#3-problems-in-traditional-retail--how-csms-solves-them)
4. [Technology Stack & Architecture](#4-technology-stack--architecture)
5. [Core Functional Modules](#5-core-functional-modules)
6. [Database Design, Stored Procedures & Data Integrity](#6-database-design-stored-procedures--data-integrity)
7. [Security, Access Control & Audit Compliance](#7-security-access-control--audit-compliance)
8. [Performance & Engineering Optimizations](#8-performance--engineering-optimizations)
9. [User Interface & Design System](#9-user-interface--design-system)
10. [Slide-by-Slide Presentation Script](#10-slide-by-slide-presentation-script)
11. [Anticipated Questions & Defense Answers](#11-anticipated-questions--defense-answers)

---

## 1. Executive Summary

The **Clothing Sales Management System (CSMS)** is an enterprise-grade retail Point of Sale (POS), Inventory Control, and Procurement Management platform tailored specifically for fashion boutiques and apparel businesses.

Apparel retail presents unique complexities compared to general retail:
- Every garment exists across a multi-dimensional matrix of styles, sizes, and colors (SKUs).
- High volume of transactions at the sales counter during peak shopping periods.
- High risk of stock shrinkage, phantom inventory, and price inconsistencies across variants.
- Constant restock cycles requiring precise supplier purchase orders and receiving logs.

CSMS bridges modern web application usability with enterprise database durability. Built on **Spring Boot 4.1.1 (Java 17)** and powered by **Oracle Enterprise Database (PL/SQL)**, CSMS enforces transactional safety directly at the database engine layer while providing a responsive, editorial-themed web application for store cashiers, floor managers, and administrators.

---

## 2. Project Objectives

1. **Eliminate Inventory Discrepancies and Overselling**:
   Provide atomic stock management where physical count and system count remain in lockstep through automated tracking of every unit movement (`IN`, `OUT`, `ADJUSTMENT`).

2. **Streamline High-Speed Checkout**:
   Empower cashiers with an intuitive, keyboard-accessible POS interface that finishes sales, applies discounts, registers multiple payment methods, and prints or previews receipts in seconds.

3. **Enforce Financial and Audit Accountability**:
   Guarantee that no sale, return, refund, or stock modification occurs untracked. Every state transition is recorded in an immutable audit ledger with timestamps and user identifiers.

4. **Automate the Supply Chain Reorder Cycle**:
   Proactively alert managers when variant stock drops below safety thresholds and enable 1-click generation and receiving of Purchase Orders from certified vendors.

5. **Provide Deep Business Intelligence**:
   Equip decision-makers with real-time financial reporting: gross revenue, net margin, cost valuation, category velocity, top-performing cashiers, and seasonal trends.

---

## 3. Problems in Traditional Retail & How CSMS Solves Them

| Challenge in Traditional Apparel Retail | Consequence | How CSMS Solves It | Technical Implementation |
| :--- | :--- | :--- | :--- |
| **Multi-attribute Variant Chaos** | Cashiers pick wrong sizes or colors; stock reports become inaccurate. | Strict Hierarchical Data Model (`Category` -> `Product` -> `Variant` with `Size` + `Color` + unique `SKU`). | Composite unique constraints; automated SKU generation; barcode-ready inputs. |
| **Phantom Inventory & Overselling** | Customer buys item that isn't on the rack; cart checkout fails or causes negative stock. | Database-level row locking (`FOR UPDATE`) and stock validation procedures before completion. | Oracle package `PKG_INVENTORY` with `REDUCE_STOCK` throwing strict application errors on insufficient stock. |
| **Unauthorized Discounts & Fraud** | Cashiers arbitrarily reduce prices at counter or pocket cash refunds. | Role-based authorization; discount validation; immutable payment records with mandatory reversal reasons. | `Spring Security` role checks (`ROLE_ADMIN` vs `ROLE_CASHIER`), `SaleReversalService` audit trails, and DB constraints. |
| **Manual Reordering Delays** | Popular garments run out of stock before anyone notices, causing lost revenue. | Real-time low stock calculation, visual urgency badges, and one-click quick/batch purchase orders. | `V_PRODUCT_STOCK` view, `LowStockReorder` service, dynamic restock modal. |
| **Brute Force & Credential Misuse** | Shared passwords, unauthorized off-shift logins, compromised accounts. | Account lockout after failed attempts, session management, and inactive employee checks. | `LoginAttemptService` with in-memory lockouts, `DisabledException` dispatching, BCrypt password hashing. |
| **Slow Reporting & Heavy Queries** | Point of Sale system freezes when management runs end-of-month financial reports. | Aggregation pushed to database views and SQL grouping instead of loading thousands of entities in memory. | `findPaidTotalsBySaleIds`, Oracle analytical views (`V_DAILY_SALES`, `V_CATEGORY_SALES`). |

---

## 4. Technology Stack & Architecture

### High-Level Architectural Pattern
CSMS implements a **Clean Layered Architecture** with database-enforced integrity:

```
[ Client Presentation Tier ]
      |  Thymeleaf Server-Rendered HTML5 + Bootstrap 5 + Vanilla CSS
      |  Modular Vanilla JavaScript (Asynchronous fetch, CSRF token handling)
      v
[ Security & Controller Tier ]
      |  Spring Security 6.x (Form login, Role-Based Access Control, Session Guards)
      |  Page Controllers (View rendering) & REST Controllers (API endpoints)
      v
[ Business Logic & Service Tier ]
      |  Spring Service Layer (@Transactional boundaries)
      |  Spring Data JPA Repositories (Entity CRUD & complex JPQL queries)
      |  Audit Trail & Cloudinary Asset Service
      v
[ Database Engine Tier ]
      |  Oracle Database 23c / 19c Enterprise Edition
      |  PL/SQL Stored Packages: PKG_INVENTORY (ADD_STOCK, REDUCE_STOCK, ADJUST_STOCK)
      |  Stored Procedures: SP_ADD_SALE_ITEM, SP_COMPLETE_SALE, SP_RECORD_PAYMENT
      |  Database Triggers, Sequences, and Foreign Key Cascades
```

### Technology Breakdown

- **Runtime & Language**: Java 17 LTS, executing on OpenJDK JVM with modern Java features (records, switch expressions, text blocks).
- **Core Framework**: Spring Boot 4.1.1
  - `spring-boot-starter-web`: RESTful endpoints and Spring MVC controller routing.
  - `spring-boot-starter-data-jpa`: Hibernate 6 ORM entity management.
  - `spring-boot-starter-security`: Authentication, authorization, password hashing, and CSRF protection.
  - `spring-boot-starter-validation`: Jakarta Bean Validation (`@NotNull`, `@NotBlank`, `@Size`, `@Min`).
  - `spring-boot-starter-jdbc`: `JdbcTemplate` and `SimpleJdbcCall` for high-speed PL/SQL execution.
  - `spring-boot-starter-thymeleaf`: Clean, fast server-side HTML rendering.
- **Database Systems**:
  - **Production**: Oracle Database (ojdbc17 23.26.3.0.0) utilizing advanced PL/SQL packages, stored procedures, and triggers.
  - **Testing**: H2 In-Memory Database (`MODE=Oracle`) for rapid automated unit and integration tests.
- **Cloud Media Pipeline**: Cloudinary Java SDK v2 (HTTP5) for automatic image optimization and CDN distribution, with graceful fallback to local storage (`/uploads/`).
- **Build & Quality Assurance**:
  - Gradle 9.7.1 build system.
  - JUnit 5 & Mockito test suites covering business rules, security authorization, and controller endpoints.

---

## 5. Core Functional Modules

### Module 1: Point of Sale (POS) & Cashier Counter
- **Product Catalog Selector**: Quick SKU and product search with real-time stock indicators.
- **Cart Management**: Add variants, update quantities with bounds checking, calculate line totals, subtotal, and tax.
- **Customer Linking**: Link sale to registered loyalty customer or anonymous walk-in.
- **Payment Processing**: Multi-payment support (Cash, ABA KHQR, Credit/Debit Card, Bank Transfer) with reference numbers and change calculation.
- **Receipt Generation**: Printable thermal-style receipt preview with complete itemization.
- **Sale Reversal / Cancellation**: Administrative cancellation workflow that safely restores stock to inventory, marks payments as refunded, and records audit reasons.

### Module 2: Inventory & Stock Management
- **Inventory Valuation**: Tabular view of all variants detailing Cost Price, Retail Price, In-Stock Quantity, Total Cost Valuation, and Total Retail Valuation.
- **Low Stock Monitoring**: Automated identification of variants with inventory below minimum safety threshold (<= 10 units), color-coded by urgency (Critical vs Warning).
- **Manual Stock Adjustment**: Dedicated modal dialog allowing managers to reconcile physical counts with system counts, recording stock delta and formal adjustment notes.
- **Stock Movement Ledger**: Complete chronological ledger of every single inventory change (`IN`, `OUT`, `ADJUSTMENT`) referencing the exact PO, Sale, or manual reason.

### Module 3: Catalog & Product Configuration
- **Product & Variant Matrix**: Manage Master Products (brand, category, description, image) and Variant Child Records (size, color, barcode SKU, cost price, sale price).
- **Cloudinary Image Asset Sync**: Single-action or automated upload of product photography to Cloud CDN with local fallback.
- **Categorization**: Multi-level categorization for apparel (e.g., Outerwear, Denim, Formal Shirts, Activewear).

### Module 4: Purchasing & Vendor Management
- **Supplier Directory**: Supplier contact details, company information, and active status tracking.
- **Purchase Order Workflow**: Draft POs with multiple variant lines, specified unit cost, and order notes.
- **Instant vs Deferred Receiving**: Option to receive stock immediately at creation or keep PO in `PENDING` until shipment arrival.
- **Quick Restock & Batch Reordering**: 1-click reorder modal from low-stock alerts and batch checkboxes to restock multiple low variants in a single PO.

### Module 5: Reporting & Financial Analytics
- **Time-Period Selectors**: Instant filtering across Daily, Weekly, Monthly, Yearly, or All-Time periods.
- **Revenue & Profit Margins**: Subtotal, order discounts, net sales, cost of goods sold (COGS), and calculated gross margin.
- **Category Performance**: Breakdown of volume and dollar sales by clothing categories.
- **Cashier Accountability**: Transaction volume, average ticket size, and total collections per staff member.

### Module 6: User Control & Security Administration
- **Employee Management**: Staff records (Name, Role, Phone, Email, Hire Date, Status).
- **User Accounts & Roles**: Secure credentials mapped to `ADMIN` or `CASHIER`.
- **Brute Force Defense**: Real-time tracking of failed login attempts, locking accounts after 5 consecutive failures.
- **Audit Trails**: Searchable system activity log showing user, action type, table modified, record ID, and exact change details.

---

## 6. Database Design, Stored Procedures & Data Integrity

### Entity Relationship Model Highlights
The database follows strict 3rd Normal Form (3NF) relational design:

```
[EMPLOYEE] 1 <---> 1 [APP_USER] 1 <---> M [USER_ROLE] M <---> 1 [APP_ROLE]
    |                      |
    | manages              | conducts
    v                      v
[PURCHASE]            [SALE] 1 <---> M [SALE_DETAIL] M <---> 1 [PRODUCT_VARIANT]
    |                      |                                          ^
    | 1:M                  | 1:M                                      | 1:M
    v                      v                                          |
[PURCHASE_DETAIL]     [PAYMENT]                                  [PRODUCT]
    |                                                                 | M:1
    +-------------------> [STOCK_MOVEMENT] <------------------------- [CATEGORY]
```

### Why Stored Procedures & Packages?
Unlike basic CRUD applications that update tables through multiple separate queries, CSMS executes mission-critical business transactions inside Oracle Stored Procedures:

1. **`PKG_INVENTORY.ADJUST_STOCK`**:
   - Performs a pessimistic row lock (`FOR UPDATE`) on the target `PRODUCT_VARIANT`.
   - Validates that new quantity is non-negative.
   - Calculates exact delta between current stock and target stock.
   - Updates `PRODUCT_VARIANT.STOCK_QTY` and sets `UPDATED_AT = SYSTIMESTAMP`.
   - Inserts an immutable audit entry into `STOCK_MOVEMENT` with type `IN` or `OUT`, reference `ADJUSTMENT`, delta quantity, and reason note.

2. **`PKG_INVENTORY.ADD_STOCK` & `REDUCE_STOCK`**:
   - Validates positive quantity and permitted reference types (`'PURCHASE'`, `'SALE'`, `'RETURN'`, `'ADJUSTMENT'`, `'OPENING'`).
   - `REDUCE_STOCK` verifies available quantity and raises application error `-20008: Insufficient stock` if reduction exceeds stock on hand.

3. **`SP_COMPLETE_SALE`**:
   - Locks the sale record and all line items.
   - Verifies all items have valid catalog pricing.
   - Atomically transitions status to `COMPLETED` and calculates final totals.

---

## 7. Security, Access Control & Audit Compliance

### Defense in Depth Architecture

1. **Role-Based Access Control (RBAC)**:
   - **`ADMIN`**: Full permissions across Inventory, Purchasing, Catalog, Staff Management, Audit Logs, and Analytics.
   - **`CASHIER`**: Restricted strictly to POS Sales Counter (`/sales/**`), Customer lookups (`/api/customers/**`), and self-profile views. Direct administrative paths (`/products/**`, `/stock/**`, `/suppliers/**`, `/user-control/**`) are denied with HTTP 403 Forbidden.

2. **Account Status & Login Safety**:
   - Passwords encoded with adaptive BCrypt hashing.
   - `CustomUserDetailsService` enforces that both the user account and linked employee record are `ACTIVE`. Disabled accounts are redirected to `/login?disabled`.
   - `LoginAttemptService` tracks IP and username failures. 5 bad attempts triggers automatic temporary lockout.

3. **Cross-Site Request Forgery (CSRF)**:
   - Synchronizer CSRF tokens embedded in all HTML forms.
   - Global asynchronous JavaScript API requests automatically inject `X-CSRF-TOKEN` headers for all state-modifying requests (`POST`, `PUT`, `DELETE`).

4. **Audit Logging**:
   - Every financial and stock modification triggers `AuditTrailService.record(...)`.
   - Logs capture exact database user, timestamp, table name, action type, record ID, and human-readable explanation.

---

## 8. Performance & Engineering Optimizations

During recent system benchmarking and hardening, major performance bottlenecks were identified and optimized:

1. **Payment Aggregation Optimization**:
   - *Previous*: Loading all payments across all years into JVM memory and running a Java stream filter.
   - *Improved*: Implemented `PaymentRepository.findPaidTotalsBySaleIds` which executes SQL `GROUP BY p.sale.saleId` and returns aggregated totals in a single indexed query.

2. **Customer Search Optimization**:
   - *Previous*: `CustomerApiController` fetched all customers and filtered in Java.
   - *Improved*: Replaced with `CustomerRepository.searchCustomers` utilizing SQL `LIKE` pattern matching on indexed name, phone, and email fields.

3. **Cloudinary Bulk Image Sync**:
   - *Previous*: Iterating through all database products for every uploaded file (N+1 query issue).
   - *Improved*: Executing targeted batch update `ProductRepository.updateImageUrl(localPath, secureUrl)`.

4. **Validation Error Standardization**:
   - Integrated `MethodArgumentNotValidException` and `BindException` handlers into `GlobalExceptionHandler` to eliminate HTTP 500 errors on invalid forms, returning structured HTTP 400 JSON payloads with exact field errors.

---

## 9. User Interface & Design System

The application features a custom, high-end **Old School / Classic Editorial Fashion** design system:

### Color Hierarchy
- **Deep Warm Ink (`#25241f`, `--ink`)**: Headings, primary text, dark buttons, active badges.
- **Warm Oatmeal Ground (`#f4f1e9`, `--ground`)**: Page backdrops, subtle contrast backgrounds.
- **Clean Off-White Paper (`#fffefa`, `--paper`)**: Card surfaces, modal dialogs, tables, input fields.
- **Soft Cream (`#ece8dd`, `--soft`)**: Table headers, active pill tabs, subtle highlights.
- **Vintage Gray Line (`#d9d5cb`, `--line`)**: Crisp structural borders and table row dividers.
- **Subdued Forest Olive (`#4f5c46`, `--bs-success`)**: Positive financial figures, in-stock badges.
- **Vintage Brick Rust (`#8d4b40`, `--bs-danger`)**: Low stock warnings, error banners.

### Typography
- **Headings & Titles**: Classic serif typography (`Georgia, 'Times New Roman', serif`) creating a premium boutique atmosphere.
- **UI Data & Controls**: Clean, high-legibility sans-serif (`Arial, Helvetica, sans-serif`) for tabular numbers, buttons, and form inputs.
- **Clean & Professional**: Zero decorative emojis; clean text labels, status badges, and standard icons.

---

## 10. Slide-by-Slide Presentation Script

Use this section as your direct script when presenting this project to instructors, stakeholders, or defense panels:

### Slide 1: Title & Introduction
- **Slide Title**: Clothing Sales Management System (CSMS) - Enterprise Retail & POS Solution
- **Presenter**: [Your Name / Team Name]
- **Key Talking Points**:
  - "Good morning/afternoon. Today I am presenting the Clothing Sales Management System, an enterprise retail and inventory platform engineered specifically for apparel retail operations."
  - "Apparel retail requires managing complex variant combinations (sizes, colors, SKUs) while maintaining high speed at checkout and strict financial accountability."

### Slide 2: The Problem Statement
- **Slide Title**: Real-World Challenges in Fashion Retail
- **Key Talking Points**:
  - "Traditional retail operations face three critical risks: stock discrepancies between the rack and the register, slow manual checkout queues, and unauthorized discounts or unrecorded inventory shrinkage."
  - "When a system updates stock purely through unconstrained web forms, network glitches or race conditions cause negative inventory and lost revenue."

### Slide 3: Solution & System Objectives
- **Slide Title**: Our Solution: Dual-Layer Durability
- **Key Talking Points**:
  - "CSMS solves this with a two-tiered architectural approach: a high-speed Spring Boot web application for store staff, backed by Oracle PL/SQL database procedures for guaranteed ACID transactional integrity."
  - "Every sale, purchase, and stock adjustment is validated by stored database packages before any record is committed."

### Slide 4: System Architecture & Technology Stack
- **Slide Title**: Robust & Scalable Architecture
- **Key Talking Points**:
  - "Our backend leverages Spring Boot 4.1.1 on Java 17, combining Spring Data JPA with Spring Security and JDBC Template."
  - "For data persistence, we employ Oracle Database 23c using stored packages like `PKG_INVENTORY`."
  - "Our frontend uses Thymeleaf server-side templates with modular JavaScript, ensuring lightning-fast page loads without heavy frontend framework overhead."
  - "Image assets are delivered via Cloudinary CDN with automatic local disk fallback."

### Slide 5: Point of Sale (POS) Live Demo / Workflow
- **Slide Title**: Fast & Reliable Point of Sale
- **Key Talking Points**:
  - "The POS module enables cashiers to search variants by SKU or name, verify real-time stock, link loyalty customers, and process payments across Cash, ABA QR, and Card."
  - "When a sale completes, the system atomically updates inventory, calculates tax, generates a printable receipt, and locks the sale against tampering."

### Slide 6: Inventory Control & Stock Reconciliation
- **Slide Title**: Real-Time Inventory & Stock Movements
- **Key Talking Points**:
  - "The inventory module displays comprehensive stock valuation at cost and retail."
  - "Variants with ten or fewer units automatically trigger visual alerts on the Low Stock tab."
  - "Our new Stock Adjustment feature enables managers to perform physical inventory reconciliations with mandatory reason auditing."

### Slide 7: Purchasing & Automated Restock Pipeline
- **Slide Title**: Supply Chain & Purchase Orders
- **Key Talking Points**:
  - "Rather than manually creating orders, managers can restock with 1-click Quick Restock or multi-item Batch Reorder modals directly from low stock alerts."
  - "Purchase orders support instant warehouse receiving or pending status for incoming shipments."

### Slide 8: Security, Role Governance & Audit Compliance
- **Slide Title**: Security Architecture & Audit Logging
- **Key Talking Points**:
  - "We enforce role-based access control: cashiers are strictly limited to the sales counter, while administrators manage pricing, staff, and purchasing."
  - "Failed login attempts trigger automated account lockouts, and every significant update is permanently logged in the audit trail."

### Slide 9: Performance Benchmarking & Hardening
- **Slide Title**: Performance Optimizations & Resilience
- **Key Talking Points**:
  - "We optimized heavy queries by moving payment aggregations directly into database SQL (`GROUP BY`), indexing customer searches, and implementing single-query batch updates for image synchronization."
  - "All automated test suites pass with 100% success rate across security, business rules, and REST controllers."

### Slide 10: Conclusion & Future Enhancements
- **Slide Title**: Project Conclusion & Future Roadmap
- **Key Talking Points**:
  - "CSMS provides a complete, reliable, and aesthetically refined platform that modernizes apparel store management."
  - "Future roadmap items include hardware barcode scanner integration, SMS receipt delivery, and AI-driven demand forecasting based on seasonal sales patterns."
  - "Thank you, and I now welcome your questions."

---

## 11. Anticipated Questions & Defense Answers

### Q1: Why did you use Oracle PL/SQL stored procedures instead of handling all business logic in Spring Boot Java services?
**Answer**:
"In retail environments, concurrent checkouts can cause race conditions where two cashiers attempt to sell the last unit of a shirt simultaneously. By using Oracle stored procedures (`PKG_INVENTORY.REDUCE_STOCK`) with row-level pessimistic locks (`SELECT FOR UPDATE`), the database guarantees that stock checks and updates are atomic. Even if multiple application servers were running, inventory overselling is physically impossible at the database engine level."

### Q2: How does the system handle an unauthorized cashier attempting to access management pages?
**Answer**:
"Spring Security enforces strict URL matching in [SecurityConfig.java](file:///Users/tansaphea/IdeaProjects/Clothing%20Sales%20Management%20System/src/main/java/com/clothing/app/config/SecurityConfig.java). Any attempt by a cashier to access `/products/**`, `/stock/**`, `/suppliers/**`, or `/user-control/**` immediately triggers an HTTP 403 Access Denied response, and the unauthorized attempt is blocked before any controller code executes."

### Q3: What happens if Cloudinary is offline or credentials expire when uploading an image?
**Answer**:
"The `FileUploadController` implements a resilient fallback pattern. It first attempts to upload to Cloudinary. If Cloudinary is unconfigured or encounters a network failure, the system catches the exception, logs a warning, and saves the file safely to local disk storage in the `/uploads/` directory, ensuring zero interruption to the user."

### Q4: How is stock managed if a customer returns an item or a sale is cancelled?
**Answer**:
"We implemented a non-destructive reversal workflow in `SaleReversalService`. When an administrator cancels a completed sale, the service iterates through the sale items and calls `PKG_INVENTORY.ADD_STOCK` with reference type `'RETURN'`, marks the payments as `'REFUNDED'`, appends the cancellation reason to the sale record, and logs an audit trail. The financial history is preserved rather than hard-deleted."

### Q5: How did you ensure high performance when viewing the sales list with hundreds of transactions?
**Answer**:
"Previously, the page loaded all historical payments into Java memory. We resolved this by adding `PaymentRepository.findPaidTotalsBySaleIds`, which passes only the visible page's sale IDs into a single SQL query that groups and sums payments directly in the database engine, reducing memory overhead and database roundtrips to negligible amounts."

---

*Document compiled for Clothing Sales Management System (CSMS) project defense and presentation.*
