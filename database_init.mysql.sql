create schema offgrid;

#

grant all privileges on offgrid.* to 'offgrid'@'%';

flush privileges;

#

create table Users
(
    id                 int auto_increment
        primary key,
    username           varchar(20)          not null,
    email              varchar(50)          not null,
    is_email_confirmed tinyint(1) default 0 not null,
    constraint Users_email_unique
        unique (email)
);

create table Authorizations
(
    user_id         int  not null,
    password_hashed blob not null,
    role            int  not null,
    constraint fk_Authorizations_user_id_id
        foreign key (user_id) references Users (id)
            on delete cascade
);

create table Extra_Permissions
(
    authorization_id int                  not null,
    permission_id    varchar(5)           not null,
    is_shield        tinyint(1) default 1 not null,
    constraint fk_ExtraPermissions_authorization_id_user_id
        foreign key (authorization_id) references offgrid.Authorizations (user_id)
            on delete cascade
);