package com.kunirank.api.ranking.discovery;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rankings")
public class RankingDiscoveryController {
    private final RankingDiscoveryService service;
    public RankingDiscoveryController(RankingDiscoveryService service) { this.service=service; }
    @GetMapping("/catalog")
    public java.util.List<RankingDiscoveryService.Card> catalog() { return service.catalog(); }
    @GetMapping("/indicators/{slug}")
    public RankingDiscoveryService.Page indicator(@PathVariable String slug,@RequestParam(required=false) Integer year,
            @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String region,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="60") int size) {
        return service.indicator(slug,year,q,region,Math.max(0,page),Math.max(1,Math.min(100,size)));
    }
    @GetMapping("/admissions")
    public RankingDiscoveryService.Page admissions(@RequestParam(defaultValue="four") String kind,@RequestParam(defaultValue="SUSI") String phase,
            @RequestParam(required=false) Integer year,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String region,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="60") int size) {
        return service.admissions(kind,phase,year,q,region,Math.max(0,page),Math.max(1,Math.min(100,size)));
    }
}
