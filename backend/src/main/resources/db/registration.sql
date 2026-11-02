-- Narrow security-definer function: caller cannot choose staff/superuser status or modify existing users.
CREATE OR REPLACE FUNCTION public.kunirank_register_member(p_username text,p_hash text,p_email text)
RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE created_id bigint;
BEGIN
 IF length(p_username)<1 OR length(p_username)>150 OR length(p_email)>254
    OR p_hash !~ '^pbkdf2_sha256\$1200000\$[A-Za-z0-9_-]{24}\$[A-Za-z0-9+/]{43}=$' THEN
   RAISE EXCEPTION 'invalid registration' USING ERRCODE='22023';
 END IF;
 INSERT INTO public.auth_user(username,password,email,first_name,last_name,is_active,is_staff,is_superuser,date_joined)
 VALUES(p_username,p_hash,p_email,'','',true,false,false,CURRENT_TIMESTAMP) RETURNING id INTO created_id;
 RETURN created_id;
END $$;;
REVOKE ALL ON FUNCTION public.kunirank_register_member(text,text,text) FROM PUBLIC;;
