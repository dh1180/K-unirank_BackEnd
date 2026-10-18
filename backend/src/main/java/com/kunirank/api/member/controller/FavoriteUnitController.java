package com.kunirank.api.member.controller;

import com.kunirank.api.member.repository.FavoriteUnitRepository;
import com.kunirank.api.member.security.MemberSessionService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/member/favorites/units")
public class FavoriteUnitController {
    private final MemberSessionService sessions;
    private final FavoriteUnitRepository favorites;

    public FavoriteUnitController(MemberSessionService sessions, FavoriteUnitRepository favorites) {
        this.sessions = sessions;
        this.favorites = favorites;
    }

    @GetMapping
    public List<FavoriteUnitRepository.Favorite> list(
            @CookieValue(name = "kunirank_member", required = false) String cookie) {
        return favorites.list(sessions.requireMember(cookie).id());
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@CookieValue(name = "kunirank_member", required = false) String cookie,
                    @PathVariable long id) {
        favorites.add(sessions.requireMember(cookie).id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CookieValue(name = "kunirank_member", required = false) String cookie,
                       @PathVariable long id) {
        favorites.remove(sessions.requireMember(cookie).id(), id);
    }
}
