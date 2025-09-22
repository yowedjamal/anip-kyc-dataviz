<?php

namespace App\Repositories\Eloquent;

use App\Repositories\DemographicRepository;
use Carbon\Carbon;

class DemographicEloquentRepository implements DemographicRepository
{
    public function ageDistribution(string $from, string $to): array
    {
        return [];
    }

    public function documentTypeDistribution(string $from, string $to): array
    {
        return [];
    }

    public function successRatesByCategory(string $from, string $to): array
    {
        return [];
    }
}
