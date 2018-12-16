drop table if exists t;
create table t (x text);

--> var
select x from t;

--> result
select ${var};
