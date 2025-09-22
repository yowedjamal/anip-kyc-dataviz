<?php

use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| Web Routes
|--------------------------------------------------------------------------
|
| Here is where you can register web routes for your application. These
| routes are loaded by the RouteServiceProvider and all of them will
| be assigned to the "web" middleware group. Make something great!
|
*/

Route::get('/', function () {
    return view('welcome');
});

// Minimal Prometheus metrics endpoint for local prometheus scraping
Route::get('/metrics', function () {
    $now = microtime(true);
    $uptime = (int)($now - ($_SERVER['REQUEST_TIME_FLOAT'] ?? $now));

    $lines = [];
    // application info metric
    $lines[] = "# HELP anip_dashboard_info Dashboard service info";
    $lines[] = "# TYPE anip_dashboard_info gauge";
    $lines[] = 'anip_dashboard_info{version="' . (env('APP_VERSION', 'dev')) . '"} 1';

    // uptime metric (seconds)
    $lines[] = "# HELP anip_dashboard_uptime_seconds Uptime of the dashboard PHP process";
    $lines[] = "# TYPE anip_dashboard_uptime_seconds gauge";
    $lines[] = "anip_dashboard_uptime_seconds {$uptime}";

    return response(implode("\n", $lines) . "\n", 200)
        ->header('Content-Type', 'text/plain; version=0.0.4');
});
