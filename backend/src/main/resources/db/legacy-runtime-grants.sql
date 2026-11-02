-- Apply as the existing database owner after registration.sql, never as an application user.
GRANT EXECUTE ON FUNCTION public.kunirank_register_member(text,text,text) TO kunirank_spring_member;
GRANT SELECT ON public.ranking_boards TO kunirank_spring_member;
GRANT SELECT(session_id,session_key,user_id) ON public.vote_sessions TO kunirank_spring_member;
GRANT UPDATE(user_id) ON public.vote_sessions TO kunirank_spring_member;
GRANT SELECT,INSERT ON public.personal_results TO kunirank_spring_voting;
-- The read-only API already connects as the existing owner with transaction read-only enforced.
-- If a dedicated read-only role is used later, grant SELECT ON personal_results to that role only.
