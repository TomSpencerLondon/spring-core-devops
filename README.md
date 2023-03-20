# Spring Framework DevOps on AWS

### Externalising Properties
First we externalise properties to make our build portable.

```java


@Configuration
@PropertySource("classpath:testing.properties")
public class ExternalPropsPropertySourceTestConfig {

    @Value("${guru.jms.server}")
    String jmsServer;

    @Value("${guru.jms.port}")
    Integer jmsPort;

    @Value("${guru.jms.user}")
    String jmsUser;

    @Value("${guru.jms.password}")
    String jmsPassword;

    @Bean
    public static PropertySourcesPlaceholderConfigurer properties() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer();

        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    public FakeJmsBroker fakeJmsBroker() {
        FakeJmsBroker fakeJmsBroker = new FakeJmsBroker();
        fakeJmsBroker.setUrl(jmsServer);
        fakeJmsBroker.setPort(jmsPort);
        fakeJmsBroker.setUser(jmsUser);
        fakeJmsBroker.setPassword(jmsPassword);

        return fakeJmsBroker;
    }
}

```

This refers to testing.properties in the test folder:
```properties
guru.jms.server=10.10.10.123
guru.jms.port=3330
guru.jms.user=Ron
guru.jms.password=Burgundy
```
The properties can be overridden by environment variables. Spring Boot lets you externalize your configuration so that you can work with the same application code in different environments. You can use properties files, YAML files, environment variables, and command-line arguments to externalize configuration. Property values can be injected directly into your beans by using the @Value annotation, accessed through Spring’s Environment abstraction, or be bound to structured objects through @ConfigurationProperties.
Spring Boot uses a very particular PropertySource order that is designed to allow sensible overriding of values. Properties are considered in the following order:

1. Devtools global settings properties on your home directory (~/.spring-boot-devtools.properties when devtools is active).
2. @TestPropertySource annotations on your tests.
3. properties attribute on your tests. Available on @SpringBootTest and the test annotations for testing a particular slice of your application.
4. Command line arguments.
5. Properties from SPRING_APPLICATION_JSON (inline JSON embedded in an environment variable or system property).
6. ServletConfig init parameters.
7. ServletContext init parameters.
8. JNDI attributes from java:comp/env.
9. Java System properties (System.getProperties()).
10. OS environment variables.
11. A RandomValuePropertySource that has properties only in random.*.
12. Profile-specific application properties outside of your packaged jar (application-{profile}.properties and YAML variants).
13. Profile-specific application properties packaged inside your jar (application-{profile}.properties and YAML variants).
14. Application properties outside of your packaged jar (application.properties and YAML variants).
15. Application properties packaged inside your jar (application.properties and YAML variants).
16. @PropertySource annotations on your @Configuration classes.
17. Default properties (specified by setting SpringApplication.setDefaultProperties).

### Using Spring Profiles
We can use Spring Profiles to load the appropriate data source. If the bean does not have an active profile it is brought in.
Beans with a dev profile will get ignored in production.

```java
public interface FakeDataSource {
    String getConnectionInfo();
}

```

We then have different profiles for each source:
```java
@Component
@Profile("dev")
public class DevDataSource implements FakeDataSource{
    @Override
    public String getConnectionInfo() {
        return "I'm dev data source";
    }
}
```

We can then use one of those profiles in our test:
```java

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DataSourceConfig.class)
@ActiveProfiles("dev")
public class DataSourceTest {
    private FakeDataSource fakeDataSource;
    
    @Autowired
    public void setFakeDataSource(FakeDataSource fakeDataSource) {
        this.fakeDataSource = fakeDataSource;
    }

    @Test
    void testDataSource() {
        System.out.println(fakeDataSource.toString());
    }
}

```

### Setting the active profile at runtime
Spring offers a number of ways to set the active profile at runtime. 

### Using Databases
We are going to use AWS RDS for our production environment. We will have h2 for dev.
MySQL local for QA and in production we will use AWS. We will use a separate jar for our pom for mysql data source:
```xml

		<dependency>
			<groupId>com.h2database</groupId>
			<artifactId>h2</artifactId>
			<scope>runtime</scope>
		</dependency>

		<dependency>
			<groupId>mysql</groupId>
			<artifactId>mysql-connector-java</artifactId>
			<scope>runtime</scope>
		</dependency>
```
Here we have added h2 for dev and the mysql connector jar. Set up mysql locally and check the connection:
```bash
tom@tom-ubuntu:~/.m2/repository/org$ sudo mysql -u root
Welcome to the MySQL monitor.  Commands end with ; or \g.
Your MySQL connection id is 9
Server version: 8.0.32-0ubuntu0.22.10.2 (Ubuntu)

Copyright (c) 2000, 2023, Oracle and/or its affiliates.

Oracle is a registered trademark of Oracle Corporation and/or its
affiliates. Other names may be trademarks of their respective
owners.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

mysql> create database springguru
    -> ;
Query OK, 1 row affected (0.01 sec)

mysql> 

```
Here we are using the root user. We then set the properties for our QA environment:
```properties
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
spring.datasource.url=jdbc:mysql://localhost:3306/springguru
spring.datasource.username=root
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
```
We then set the active profile to qa. You may need to change the permissions for connecting to mysql:
```bash
mysql > ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '';
mysql > FLUSH PRIVILEGES;
```

Mysql Workbench connection issues on linux. On a terminal window, run the following commands:
```bash
# snap connect mysql-workbench-community:password-manager-service 
# snap connect mysql-workbench-community:ssh-keys
```
### Entity diagram of database
![springguru](https://user-images.githubusercontent.com/27693622/226150009-807edbc2-d297-4dc6-a299-3dd637888147.png)


### Creating a service account
We will create a user with restricted access. No create tables or moderate tables. This is standard practice in enterprise
applications.

```sql
CREATE USER 'springframework'@'localhost' IDENTIFIED BY 'guru';

GRANT SELECT ON springguru.* to 'springframework'@'localhost';
GRANT INSERT ON springguru.* to 'springframework'@'localhost';
GRANT DELETE ON springguru.* to 'springframework'@'localhost';
GRANT UPDATE ON springguru.* to 'springframework'@'localhost';

```

If we check in MySQL Workbench we see that our new user has limited privileges:

![image](https://user-images.githubusercontent.com/27693622/226191730-242a9258-d00c-4d9f-bc6b-8d2482033655.png)

We can then add this user for testing with the qa profile:
```properties
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
spring.datasource.url=jdbc:mysql://localhost:3306/springguru
spring.datasource.username=springframework
spring.datasource.password=guru
spring.jpa.hibernate.ddl-auto=update
```
It is now best practice to use a service account with restricted privileges.

### Encrypting Properties
We can use Jasypt for encryption of the database password:
```xml
		<dependency>
			<groupId>com.github.ulisesbocchio</groupId>
			<artifactId>jasypt-spring-boot-starter</artifactId>
			<version>3.0.5</version>
		</dependency>
```

We can then encrypt the username and password:
```bash
 ./encrypt.sh input=springframework password=password
```

And use the encrypted username and password in our config file:
```java
@Configuration
@EncryptablePropertySource(name = "qaEncryptedProperties", value = "classpath:qa.encrypted.properties")
@Profile("qa")
public class QaEncryptedConfig {
}

```