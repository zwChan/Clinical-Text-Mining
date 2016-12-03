create database chv char set utf8;
use chv;
create table cancer_ngram (
ngram            varchar(100),
train            varchar(100),
n                int,
tfdf             int,
tf               int,
df               int,
cvalue           float,
nest             float,
umls_score       float,
chv_score        float,
cui_umls         varchar(100),
cui_chv          varchar(100),
contain_umls     varchar(100),
contain_chv      varchar(100),
win_umls         int,
win_chv          int,
sent_umls        int,
sent_chv         int,
umls_dist        int,
chv_dist         int,
win_pos          varchar(100),
prefix           varchar(100),
suffix           varchar(100),
bow_total        int,
bow_words        int,
sytax            varchar(100),
nn               varchar(100),
an               varchar(100),
pn               varchar(100),
anpn             varchar(100),
isTrain          varchar(100),
capt_first       varchar(100),
capt_term        varchar(100),
capt_all         varchar(100),
stys             varchar(100),
text_org         varchar(100),
sentence         text
);
create table diabetes_ngram like cancer_ngram;
load data local infile '/data/ra/data/ngram_cancer_tf5.txt' into table cancer_ngram fields terminated by '\t' enclosed by '"' lines terminated by '\n' ignore 1 lines;
load data local infile '/data/ra/data/ngram_diabetes_tf5.txt' into table diabetes_ngram fields terminated by '\t' enclosed by '"' lines terminated by '\n' ignore 1 lines;

select n,sum(length(cui_chv)>0) as chv,sum(length(cui_chv)=0 and length(cui_umls)>0) as `umls-chv`, sum(length(cui_umls)=0) as others, count(*) as cnt from diabetes_ngram group by n;
select n,sum(length(cui_chv)>0) as chv,sum(length(cui_chv)=0 and length(cui_umls)>0) as `umls-chv`, sum(length(cui_umls)=0) as others, count(*) as cnt from cancer_ngram group by n;