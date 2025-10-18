# Cats user demo

Demo application based on cats.<br> 
Application exposes simple rest api for user management.
support: 

* Authentication based on JWT token
* User management (CRUD)
  * login user
  * refresh tokens
  * create user
  * get user by id
  * list users 
* Open api documentation endpoint

Application is covered by unit tests and also one integration test using testcontainers is developed.  

## Run
before run need to start a database. Use docker-compose to start a database in execute directory.
```
cd ~/execute
docker-compose up
```
then run the app
```bash
sbt run
```
## Test

```bash
sbt test
```


