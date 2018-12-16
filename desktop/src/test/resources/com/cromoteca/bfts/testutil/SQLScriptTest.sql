-- comments are ignored
-- queries must end with ; (semicolons are ignored in comments)

drop table if exists sqlscripttest;

create table sqlscripttest (
  number integer,
  other_number real,
  string text
);

-- DOUBLE-COLON SYNTAX

-- variable assignment: name :: value
pi :: select 3.14;

-- reference to variable in query
insert into sqlscripttest (other_number) values (${pi});

pi_id :: select rowid from sqlscripttest where other_number = ${pi};

-- this newline should be allowed
name::
select 'John';

-- spaces in use of variable
update sqlscripttest set string = ${name}, number = length(${ name }) where rowid = ${pi_id};

-- will select from the only available row
len :: select number from sqlscripttest;

-- spaces around double colons are optional
rows::select count(*) from sqlscripttest;


-- CLEANUP
delete from sqlscripttest;


-- ARROW SYNTAX

-- variable assignment:
--> pi
select 3.14;

-- reference to variable in query
insert into sqlscripttest (other_number) values (${pi});

--> pi_id
select rowid from sqlscripttest where other_number = ${pi};

-- blank lines after variable are allowed
--> name

select 'John';

-- spaces in use of variable
update sqlscripttest set string = ${name}, number = length(${ name }) where rowid = ${pi_id};

-- will select from the only available row
--> len
select number from sqlscripttest;

-- spaces after arrow are optional and newlines after variable name are accepted
-->rows
select count(*) from sqlscripttest;


