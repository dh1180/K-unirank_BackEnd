package com.kunirank.api.ranking.controller;
import com.kunirank.api.ranking.service.PersonalResultService;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class PersonalResultController {
 private final PersonalResultService results;
 public PersonalResultController(PersonalResultService results){this.results=results;}
 @GetMapping("/api/v1/results/{id}") public Map<String,Object> read(@PathVariable UUID id){return results.read(id);}
 @PostMapping("/api/v1/boards/overall/result") public Map<String,Object> create(@CookieValue(name="kunirank_vs",required=false) String cookie){return results.create(cookie);}
 @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
 public org.springframework.http.ResponseEntity<Map<String,String>> error(org.springframework.web.server.ResponseStatusException ex){
  return org.springframework.http.ResponseEntity.status(ex.getStatusCode()).body(Map.of("detail",ex.getReason()==null?"요청을 처리하지 못했습니다.":ex.getReason()));
 }
}
