CREATE TABLE IF NOT EXISTS customers (
    id IDENTITY PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(50),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS products (
    id IDENTITY PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    description CLOB,
    price DECIMAL(10,2),
    stock_quantity INTEGER DEFAULT 0,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id IDENTITY PRIMARY KEY,
    order_number VARCHAR(50) UNIQUE NOT NULL,
    customer_id BIGINT,
    status VARCHAR(50),
    subtotal DECIMAL(10,2),
    tax DECIMAL(10,2),
    shipping DECIMAL(10,2),
    total DECIMAL(10,2),
    created_at TIMESTAMP
);
