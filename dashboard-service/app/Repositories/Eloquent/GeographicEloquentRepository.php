<?php

namespace App\Repositories\Eloquent;

use App\Repositories\GeographicRepository;

class GeographicEloquentRepository implements GeographicRepository
{
    public function regionalDistribution(string $from, string $to): array
    {
        return [];
    }

    public function heatmapData(string $from, string $to): array
    {
        return [];
    }
}
