# Use PHP 8.3 CLI image to satisfy packages locked for PHP >= 8.2/8.3
FROM php:8.3-cli

WORKDIR /var/www/html

# Install system deps (minimal) and unzip for composer
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    unzip \
    libzip-dev \
    libpng-dev \
    libjpeg-dev \
    libfreetype6-dev \
    && docker-php-ext-configure gd --with-freetype --with-jpeg \
    && docker-php-ext-install -j$(nproc) gd zip \
    && rm -rf /var/lib/apt/lists/*

# Install composer
COPY --from=composer:2 /usr/bin/composer /usr/bin/composer

# Copy application files (including composer.json, composer.lock, etc.)
COPY . /var/www/html

# Install PHP dependencies (in container, after all sources are present)
RUN composer install --no-dev --no-interaction --prefer-dist

EXPOSE 8000

CMD ["php", "-S", "0.0.0.0:8000", "-t", "public"]
