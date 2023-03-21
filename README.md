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

We then add the encrypted username and password to the application.properties:
```properties
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
spring.datasource.url=jdbc:mysql://localhost:3306/springguru
jasypt.encryptor.password=password
jasypt.encryptor.iv-generator-classname=org.jasypt.iv.NoIvGenerator
jasypt.encryptor.algorithm=PBEWithMD5AndTripleDES
spring.datasource.username=ENC(3ZeqNvW+Rm0DgOkkVVXgPBxIEeyXsKQ6)
spring.datasource.password=ENC(1qHrTuI0RB4Qzl3zPE3FmQ==)
spring.jpa.hibernate.ddl-auto=update
```

### Continuous Integration
We are now going to provision services in the cloud such as jenkins and a dns for the 
jenkins instance. We will also set up Artifactory with docker so that we can deploy jenkins builds
to deploy maven artifacts to the Artifactory repository.

### Introduction to AWS
We will now look in more detail at Amazon Web Services. AWS cloud compute was created because they were not using
servers during the year. This business has now grown to make them a true leader in the space. Companies like Netflix
use AWS for deployments. It is an incredible collection of resources:

![image](https://user-images.githubusercontent.com/27693622/226225255-34c043ff-efb3-4d24-8f18-d609762dc2ff.png)

For our application we will use EC2 virtual servers in the cloud to provision a server in the cloud to run our jenkins instance.
We will use Elastic Beanstalk for deploying our Tomcat instance. We will also use RDS to provision our mysql database managed
by Amazon. Amazon will handle all backups and software upgrades on the database. This is useful for small startups who want to
out source the management of their databases. We will also use Route 53 to host our domain and DNS. We will then point traffic
at this domain.

### Linux distributions
We will use the Redhat flavour of linux. There are two branches: debian (ubuntu) and fedora. In enterprise applications
Redhat or Fedora is frequently used. The fedora linuxes are mostly standard in enterprise applications. Most often Redhat
enterprise linux is used.

### Provisioning a server on AWS
We will now go to the AWS console to set up an AMI to use jenkins.

![image](https://user-images.githubusercontent.com/27693622/226227223-06c07c7a-1212-40ca-a9cd-b61fa90bfca8.png)

I have set up a Redhat linux instance and added a security group with a port range inbound traffic on port 8080.
We now install oracle jdk and set up jenkins. Our instance is a t2 micro and will reference the private IP address. We can
also use ssh to connect to the instance.

```bash
tom@tom-ubuntu:~/Desktop$ chmod 400 springguru.pem
tom@tom-ubuntu:~/Desktop$ ssh -i "springguru.pem" ec2-user@ec2-54-236-41-164.compute-1.amazonaws.com
Register this system with Red Hat Insights: insights-client --register
Create an account or view all your systems at https://red.ht/insights-dashboard
[ec2-user@ip-172-31-52-103 ~]$ 
```
We are now inside the amazon instance. We will use wget to get the oracle instance of jdk.

```bash
> sudo su
> yum install wget
> wget https://corretto.aws/downloads/latest/amazon-corretto-11-x64-linux-jdk.tar.gz
> tar xzf amazon-corretto-11-x64-linux-jdk.tar.gz
> cp -r ./amazon-corretto-11.0.18.10.1-linux-x64/ /opt/
> cd /opt
> alternatives --install /usr/bin/java java /opt/amazon-corretto-11.0.18.10.1-linux-x64/bin/java 2
> alternatives config java

[root@ip-172-31-52-103 opt]# java -version
openjdk version "11.0.18" 2023-01-17 LTS
OpenJDK Runtime Environment Corretto-11.0.18.10.1 (build 11.0.18+10-LTS)
OpenJDK 64-Bit Server VM Corretto-11.0.18.10.1 (build 11.0.18+10-LTS, mixed mode)

> alternatives --install /usr/bin/jar jar /opt/amazon-corretto-11.0.18.10.1-linux-x64/bin/jar 2
> alternatives --install /usr/bin/javac javac /opt/amazon-corretto-11.0.18.10.1-linux-x64/bin/javac 2
> alternatives --set jar /opt/amazon-corretto-11.0.18.10.1-linux-x64/bin/jar
> alternatives --set javac /opt/amazon-corretto-11.0.18.10.1-linux-x64/bin/javac
```

A new release is produced weekly to deliver bug fixes and features to users and plugin developers. It can be installed from the redhat yum repository.
```bash

sudo wget -O /etc/yum.repos.d/jenkins.repo \
https://pkg.jenkins.io/redhat/jenkins.repo
sudo rpm --import https://pkg.jenkins.io/redhat/jenkins.io.key
sudo yum upgrade
sudo yum install jenkins


```

# Add required dependencies for the jenkins package
https://www.jenkins.io/doc/book/installing/linux/

You can enable the Jenkins service to start at boot with the command:
```bash
sudo systemctl enable jenkins
```
You can start the Jenkins service with the command:
```bash

sudo systemctl start jenkins
```
You can check the status of the Jenkins service using the command:
```bash

sudo systemctl status jenkins
```
We have now installed jenkins-2 on our EC2 instance and wnat to start the service.
```bash
> service jenkins start
> ps -ef | grep jenkins
root       11000    1029  0 02:17 pts/0    00:00:00 grep --color=auto jenkins
```
This link is very good for setting up jenkins:
https://www.jenkins.io/doc/tutorials/tutorial-for-installing-jenkins-on-AWS/

We now get this page on http://ec2-100-26-253-161.compute-1.amazonaws.com:8080/login?from=%2F

![image](https://user-images.githubusercontent.com/27693622/226494197-b5bdb0b4-baf0-494c-8eb3-7eb03e9cb4b0.png)

We get the admin password by running:
```bash
[ec2-user ~]$ sudo cat /var/lib/jenkins/secrets/initialAdminPassword

```

We then install the suggested plugins and add our first user:

![image](https://user-images.githubusercontent.com/27693622/226494602-26a30619-01de-4820-a8f7-ea8389c89d65.png)

We now have a jenkins server for configuring builds:

![image](https://user-images.githubusercontent.com/27693622/226494792-09b0b82f-e6cc-4d38-bd1a-8e0981650053.png)

### How DNS works
- stands for domain name services or domain name server
- gets you an ip address associated with a human readable text address
  - springframework.guru is IP address 172.66.43.56

```bash
[ec2-user@ip-172-31-63-3 ~]$ nslookup springframework.guru

Non-authoritative answer:
Name:	springframework.guru
Address: 172.66.43.56
Name:	springframework.guru
Address: 172.66.40.200
Name:	springframework.guru
Address: 2606:4700:3108::ac42:28c8
Name:	springframework.guru
Address: 2606:4700:3108::ac42:2b38

```

This is the result for my own dns on route 53:
```bash
[ec2-user@ip-172-31-63-3 ~]$ nslookup drspencer.io

Non-authoritative answer:
Name:	drspencer.io
Address: 18.67.76.121
Name:	drspencer.io
Address: 18.67.76.122
Name:	drspencer.io
Address: 18.67.76.43
Name:	drspencer.io
Address: 18.67.76.113

```

How DNS works:

![image](https://user-images.githubusercontent.com/27693622/226501471-b389a40d-ae08-4d40-9a99-880835ed1691.png)

When your computer boots up it asks for an IP address. This is where it looks names up from. When we request 
springframework.guru it works with top level domain. This request goes to find the dns server registered with the Internet
Server Provider. The route then goes to dns record sets which are then returned to your local machine. Cname records take
a url and convert it to springframework.guru. The DNS record then responds back with the IP address so that the computer
can find the server by the IP address.


