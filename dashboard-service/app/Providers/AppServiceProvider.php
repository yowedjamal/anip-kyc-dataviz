<?php

namespace App\Providers;

use Illuminate\Support\ServiceProvider;
use App\Repositories\AnalyticsRepository;
use App\Repositories\Eloquent\AnalyticsEloquentRepository;
use App\Repositories\DemographicRepository;
use App\Repositories\Eloquent\DemographicEloquentRepository;
use App\Repositories\GeographicRepository;
use App\Repositories\Eloquent\GeographicEloquentRepository;

class AppServiceProvider extends ServiceProvider
{
    /**
     * Register any application services.
     */
    public function register(): void
    {
        // Repository bindings
        $this->app->bind(AnalyticsRepository::class, AnalyticsEloquentRepository::class);
    $this->app->bind(DemographicRepository::class, DemographicEloquentRepository::class);
    $this->app->bind(GeographicRepository::class, GeographicEloquentRepository::class);
    }

    /**
     * Bootstrap any application services.
     */
    public function boot(): void
    {
        //
    }
}
