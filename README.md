# Offgrid

Next-generation FHE-MPC platform.


## Architecture

 - containerization: docker, docker-compose
 - backend: http4k, Kotlin, AdoptOpenJDK
 - database: MySQL
 - metrics database: Graphite
 - static file server: Nginx
 - OAuth middleware: Hydra
 - edge router: traefik
 - (optional) supervisor: netdata, portainer

## Deployment

We strongly recommend deploy Project Offgrid with our pre-defined `docker-compose.yml`, in this way you can deploy the whole stack with just “one-click” and lots environment variables will be set automatically as well. You may notice that there are two `docker-compose` files in the root folder, `docker-compose.yml` and `docker-compose.supervision.yml`. The former is contains everything consisting Project Offgrid, the latter is some supervision components which is not directly related to Project Offgrid, it is fine to omit it. Though not necessary, we still recommend both `docker-compose` file to be deployed.

### Prerequisites

`docker` and `docker-compose` should be installed in advance, and the project should be cloned: 

```shell script
$ git clone http://github.com/Ray-Eldath/offgrid
$ git clone http://github.com/Ray-Eldath/ongrid
```

After all required environment variables are set, run:

```shell script
$ cd offgrid

# if supervisor is not needed:
$ docker-compose -up

# if supervisor should be deployed: 
$ docker-compose -f docker-compose.yml -f docker-compose.supervision.yml
```

### Environment variables

Some password and secret can be generated with `cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1`. We believe such length (32) is enough for a typical application.

| Environment variable                          | Description                                                  |
| --------------------------------------------- | ------------------------------------------------------------ |
| `OFFGRID_HOST`                                | Host of the front end (ongrid) entrypoint.                   |
| `OFFGRID_DATABASE_PASSWORD`                   | Password of MySQL user `offgrid`. During the initialization process, a user `offgrid` with this password as well as a schema named `offgrid` will be created. All privileges and table creation will be handled automatically. |
| `OFFGRID_DATABASE_ROOT_PASSWORD`              | Password of MySQL user `root`, will used by `Hydra`. Should be kept carefully. |
| `OFFGRID_HYDRA_HOST`                          | Host of Hydra public service. May automatically handled by edge router if feasiable. |
| `OFFGRID_HYDRA_SECRETS_SYSTEM`                | Secret used to encrypt database of Hydra. Should be kept carefully. |
| `OFFGRID_ALIYUN_DIRECTMAIL_ACCESS_KEY_ID`     | AccessKeyId of Aliyun DirectMail service.                    |
| `OFFGRID_ALIYUN_DIRECTMAIL_ACCESS_KEY_SECRET` | AccessKeySecret of Aliyun DirectMail service.                |
| `OFFGRID_GRAFANA_HOST`                        | Host of Grafana panel public service.                        |
| `OFFGRID_GRAFANA_DOMAIN`                      | Domain of Grafana panel public service, should consists  `OFFGRID_GRAFANA_HOST`. |
| `OFFGRID_GRAFANA_OAUTH_CLIENT_SECRET`         | Create the secret with `hydra`, used for OAuth authentication. |

Many environment variables are handled by `docker-compose` automatically since inter-containers connection could established use container name solely, these environment variables are not listed above. So if you deploy Project Offgrid without the pre-defined `docker-compose.yml`, these unset variables may cause problems.

### Entrypoints

Panels and static file server will be exposed to the Internet using edge router with certain entrypoints:

| Entrypoints (`domain.com` as host name) | Service                   |
| --------------------------------------- | ------------------------- |
| `https://domain.com`                    | `frontend`                |
| `https://api.domain.com/`               | `backend`                 |
| `https://oauth.domain.com/`             | `hydra` (public API only) |
| `https://graphite.domain.com/`          | `graphite` (panel only)   |
| `https://grafana.domain.com/`           | `grafana`                 |
| `https://netdata.domain.com/`           | `netdata` (if deployed)   |
| `https://portainer.domain.com/`         | `portainer` (if deployed) |

Such entrypoints are handled by edge router with service discovery. So if the correspond service is stopped, the related entrypoint will be withdrawn and become invisible while the remaining services still exposed to the Internet.

## Disclaimer

Project Offgrid is developed by *IllegalSkillsException*. Most code-related components is developed by [Ray Eldath](https://github.com/Ray-Eldath)).

Does not guarantee availability, usability, safety as well as maintenance.