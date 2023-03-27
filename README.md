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
We have now installed jenkins-2 on our EC2 instance and start the service.
```bash
> service jenkins start
> ps -ef | grep jenkins
root       11000    1029  0 02:17 pts/0    00:00:00 grep --color=auto jenkins
```
This link is very good for setting up jenkins:
https://www.jenkins.io/doc/tutorials/tutorial-for-installing-jenkins-on-AWS/

This link is useful for font errors with headless java:
https://wiki.jenkins.io/display/JENKINS/Jenkins+got+java.awt.headless+problem

In particular this command is useful for fonts:
```bash
sudo yum install xorg-x11-server-Xvfb
```

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
can find the server by the IP address. It can take time for address changes to propagate through all the global DNS servers.

### Using Route 53
We are now going to set a domain and subdomain for our jenkins address using Route 53. We will point a subdomain at the ec2 instance
ip address. Now when we go to http://jenkins.drspencer.io:8080/login?from=%2F we can see the login page for jenkins.
Browsers run on port 80 but our jenkins server is running on 8080. We are overriding port 80 in the browser. 
We now need to set up Apache to do port forwarding between 80 and 8080. Apache routes the war file running on 8080 to 80 so
that we can view the application directly in the browser.

```bash
[ec2-user@ip-172-31-63-3 ~]$ sudo yum install httpd
Last metadata expiration check: 1:42:53 ago on Tue Mar 21 00:26:29 2023.
Dependencies resolved.
===========================================================================================
 Package                  Arch        Version                       Repository        Size
===========================================================================================
Installing:
 httpd                    x86_64      2.4.55-1.amzn2023             amazonlinux       48 k
Installing dependencies:
 apr                      x86_64      1.7.2-2.amzn2023.0.2          amazonlinux      129 k
 apr-util                 x86_64      1.6.3-1.amzn2023.0.1          amazonlinux       98 k
 generic-logos-httpd      noarch      18.0.0-12.amzn2023.0.3        amazonlinux       19 k
 httpd-core               x86_64      2.4.55-1.amzn2023             amazonlinux      1.4 M
 httpd-filesystem         noarch      2.4.55-1.amzn2023             amazonlinux       15 k
 httpd-tools              x86_64      2.4.55-1.amzn2023             amazonlinux       82 k
 mailcap                  noarch      2.1.49-3.amzn2023.0.3         amazonlinux       33 k
Installing weak dependencies:
 apr-util-openssl         x86_64      1.6.3-1.amzn2023.0.1          amazonlinux       17 k
 mod_http2                x86_64      2.0.11-2.amzn2023             amazonlinux      150 k
 mod_lua                  x86_64      2.4.55-1.amzn2023             amazonlinux       62 k

Transaction Summary
===========================================================================================
Install  11 Packages

Total download size: 2.0 M
Installed size: 6.1 M
Is this ok [y/N]: y

[ec2-user@ip-172-31-63-3 ~]$ sudo service httpd start
Redirecting to /bin/systemctl start httpd.service
```
We now have apache up and running:
![image](https://user-images.githubusercontent.com/27693622/226503394-8f1e9c60-725c-447d-a093-81e80b777e49.png)

but we are not redirecting to jenkins yet. We need to go to configure the apache daemon:

```bash
[ec2-user@ip-172-31-63-3 ~]$ sudo service httpd start
Redirecting to /bin/systemctl start httpd.service
[ec2-user@ip-172-31-63-3 ~]$ cd /etc/httpd/conf
[ec2-user@ip-172-31-63-3 conf]$ ls
httpd.conf  magic
```
We now add our jenkins setup in the apache httpd.conf file:
```bash
# Jenkins setup
<VirtualHost *:80>
    ServerName jenkins.drspencer.io
    ProxyRequests Off
    ProxyPreserveHost On
    AllowEncodedSlashes NoDecode
    ProxyPass / http://localhost:8080/ nocanon
    ProxyPassReverse / http://localhost:8080/
    ProxyPassReverse / http://jenkins.drspencer.io
    <Proxy http://localhost:8080/* >
        Order deny,allow
        Allow from all
    </Proxy>
</VirtualHost>
```
This sets up a proxy for the server and reverses traffic from 8080. We now do an apache restart:
```bash
[ec2-user@ip-172-31-63-3 conf]$ service httpd restart
Redirecting to /bin/systemctl restart httpd.service

Failed to restart httpd.service: Access denied
See system logs and 'systemctl status httpd.service' for details.
[ec2-user@ip-172-31-63-3 conf]$ sudo service httpd restart
Redirecting to /bin/systemctl restart httpd.service
Job for httpd.service failed because the control process exited with error code.
See "systemctl status httpd.service" and "journalctl -xeu httpd.service" for details.
[ec2-user@ip-172-31-63-3 conf]$ setsebool -P httpd_can_network_connect true
Cannot set persistent booleans, please try as root.
[ec2-user@ip-172-31-63-3 conf]$ sudo setsebool -P httpd_can_network_connect true
```
Because we are running on a Security-Enhanced Linux machine we have to make SE-Linux allow restart.
https://www.jenkins.io/doc/book/system-administration/reverse-proxy-configuration-with-jenkins/reverse-proxy-configuration-apache/#mod_proxy:~:text=If%20you%20are,P%20httpd_can_network_connect%20true
Our configuration is trying to connect to the tomcat instance running on 8080 and the Security settings are not allowing us.
This command allows apache to connect to the network:
```bash
setsebool -P httpd_can_network_connect true
```
We can now connect to jenkins via the domain jenkins.drspencer.io directly:

![image](https://user-images.githubusercontent.com/27693622/226509814-b0e73eb9-1b12-49d4-8881-3142465665d9.png)

We can now block port 8080. We go to the jenkins file in /etc/sysconfig and open jenkins with vim.
We then use cntrl f to page down and add a Jenkins listen address:
```bash
JENKINS_PORT="8080"

## Type:        string
## Default:     ""
## ServiceRestart: jenkins
#
# IP address Jenkins listens on for HTTP requests.
# Default is all interfaces (0.0.0.0).
#
JENKINS_LISTEN_ADDRESS="127.0.0.1"

```
This blocks jenkins listening to the world on port 8080.
We then restart jenkins:
```bash
[root@ip-172-31-63-3 sysconfig]# service jenkins restart
Restarting jenkins (via systemctl):                        [  OK  ]
```
and check for the jenkins port run information:
```bash
[root@ip-172-31-63-3 sysconfig]# ps -f | grep jenkins
root       20945   20524  0 09:17 pts/0    00:00:00 grep --color=auto jenkins
```
Jenkins is now running on jenkins, its own user account.
This is a good security set up so it is locked to the outside world.
Jenkins is available on port 80 but no longer 8080.

This is not quite enough on aws linux. I had to add iptables to block all non-local access to port 8080:
```bash
iptables -A INPUT -p tcp --dport 8080 -s localhost -j ACCEPT
iptables -A INPUT -p tcp --dport 8080 -j DROP
```
Now we can't access http://jenkins.drspencer.io:8080 from the browser. We can only access http://jenkins.drspencer.io

### Creating ssh keys

When we are working with github and jenkins we can use a username and password for checking into git. This is not security
best practice. A great way to enable communication between github is to use ssh keys. Ssh is a great way to enable encrypted
communication between two servers and also to avoid sharing secrets. We are now going to create an ssh key from the linux
command line. We are currently under the username root:
```bash
[root@ip-172-31-63-3 sysconfig]# su jenkins
[root@ip-172-31-63-3 sysconfig]# whoami
root
[root@ip-172-31-63-3 sysconfig]# ps -ef | grep jenkins
jenkins    25804       1  0 10:18 ?        00:01:47 /usr/bin/java -Djava.awt.headless=true -jar /usr/share/java/jenkins.war --webroot=/var/cache/jenkins/war --httpPort=8080
root       56019   20524  0 20:30 pts/0    00:00:00 grep --color=auto jenkins

```
Jenkins has non user account so we need to run the bash shell as jenkins:
```bash
[root@ip-172-31-63-3 sysconfig]# su -s /bin/bash jenkins
bash-5.2$ whoami
jenkins
bash-5.2$ 
```

We now go into the jenkins folder on our linux instance:
```bash
bash-5.2$ ls
acpid	 console       i18n	   man-db	    nftables.conf  selinux
atd	 cpupower      irqbalance  modules	    rngd	   sshd
chronyd  firewalld     jenkins	   network	    rpcbind	   sysstat
clock	 htcacheclean  keyboard    network-scripts  run-parts	   sysstat.ioconf
bash-5.2$ cd
bash-5.2$ ls
config.xml					nodeMonitors.xml
hudson.model.UpdateCenter.xml			nodes
hudson.plugins.git.GitTool.xml			plugins
identity.key.enc				queue.xml.bak
jenkins.install.InstallUtil.lastExecVersion	secret.key
jenkins.install.UpgradeWizard.state		secret.key.not-so-secret
jenkins.model.JenkinsLocationConfiguration.xml	secrets
jenkins.telemetry.Correlator.xml		updates
jobs						userContent
logs						users
bash-5.2$ pwd
/var/lib/jenkins
bash-5.2$ 
```
We create an .ssh folder:
```bash
bash-5.2$ mkdir .ssh
bash-5.2$ cd .ssh/
bash-5.2$ pwd
/var/lib/jenkins/.ssh
bash-5.2$ 
```
We now want to generate an ssh key:
```bash
bash-5.2$ ssh-keygen -t rsa -C 'jenkins@example.com'
Generating public/private rsa key pair.
Enter file in which to save the key (/var/lib/jenkins/.ssh/id_rsa): 
Enter passphrase (empty for no passphrase): 
Enter same passphrase again: 
Your identification has been saved in /var/lib/jenkins/.ssh/id_rsa
Your public key has been saved in /var/lib/jenkins/.ssh/id_rsa.pub
The key fingerprint is:
SHA256:3cre8EzGxywgkHxfchhxxceb0YEj5S6eRz4zAEan2Bs jenkins@example.com
The key's randomart image is:
+---[RSA 3072]----+
|         .ooo+ooo|
|     . .+ o=.o..+|
|      +..Eo +...+|
|       o.o+=.  o |
|        S.+o.o   |
|         o.+*o   |
|          +o=*+  |
|         . B.o+  |
|          . +    |
+----[SHA256]-----+

```

We add the public key to github:
![image](https://user-images.githubusercontent.com/27693622/226736467-ba252008-a6db-4407-8855-ed6782240707.png)

We now need to install git on our linux instance:
```bash
> sudo yum install git
> git --version
git version 2.39.2
```

We are now going to setup the credentials in jenkins to use them for builds.

![image](https://user-images.githubusercontent.com/27693622/226741252-5312bdc4-4e19-4258-bfb1-7e35875abcfb.png)

We also want to add maven to jenkins:
![image](https://user-images.githubusercontent.com/27693622/226743299-d3f5b23a-efaf-43bc-99ad-a621e1743761.png)

### Build configuration
We are now going to create a build configuration in jenkins. We are now going to set up the build and get things cooking
in jenkins.

![image](https://user-images.githubusercontent.com/27693622/226744537-8cb8e0b2-8d7b-4006-be38-65d630dcec7d.png)

We also have to add our private key to the jenkins build configuration and a git webhook:

![image](https://user-images.githubusercontent.com/27693622/226747793-afd920cc-f8ea-44c6-8abb-3ee2ca2a1999.png)

![image](https://user-images.githubusercontent.com/27693622/226748751-cf554bc8-159a-4dc0-a905-36ac7fbb1ff8.png)

and set up the build:
![image](https://user-images.githubusercontent.com/27693622/226749135-7e4aaa8a-4c48-4f8a-9909-71f0daaf6bd9.png)

We can check console output:
![image](https://user-images.githubusercontent.com/27693622/226906390-56a1d15e-0205-497c-8075-a1aad10bb5e3.png)

Now we have running builds in jenkins. We can now test the change by pushing to github. 
We can test this with:
```bash
git commit --allow-empty -m "Empty commit"
```
on our repository.

![image](https://user-images.githubusercontent.com/27693622/226961267-a011fae1-81b1-4ff3-b27f-e38ad1998eb2.png)

### Artifactory
We are now going to use Artifactory to store the jar builds. The advantage of Artifactory is that it is a private repository
where we can save our images. We are going to deploy Artifactory in a docker container.
We will create a new ec2 server on AWS and install docker.
To get Docker running on the AWS AMI will follow the steps below (these are all assuming you have ssh'd on to the EC2 instance).
1. Update the packages on our instance

```bash 
[ec2-user ~]$ sudo yum update -y
```

2. Install Docker

```bash
[ec2-user ~]$ sudo yum install docker -y
```

3. Start the Docker Service

```bash
[ec2-user ~]$ sudo service docker start
```

4. Add the ec2-user to the docker group so you can execute Docker commands without using sudo.

```bash
[ec2-user ~]$ sudo usermod -a -G docker ec2-user
```

### Containers
- Have their own process space
- Their own network interface
- 'Run' processes as root (inside the container)
- Have their own disk space
  - (can share with host too)

![image](https://user-images.githubusercontent.com/27693622/226969173-32fa0ed1-2318-4842-83a7-f27b3b1db0ae.png)

Containers have no Guest operating system so we are running from the operating system of the original machine.
Docker containers are upto 30% more efficient than virtual machines.

### Docker terminology
- Docker image - the representation of a docker container. Like a jar file in Java
- Docker container - standard runtime of Docker. Effectively a deployed and running Docker Image.
- Docker Engine - The code which manages Docker stuff. Creates and runs Docker Containers

Here we see the way in which Docker is put together:

![image](https://user-images.githubusercontent.com/27693622/226971788-a9e0ba92-9fb9-4e87-90ce-de1cce7e9ed5.png)

We are going to install Artifactory using volumes so that we can persist the maven repository using Artifactory.
This link was helpful:
https://medium.com/@raguyazhin/step-by-step-guide-to-install-jfrog-artifactory-on-amazon-linux-6b832dd8097b
and this one was excellent:
https://www.coachdevops.com/search?q=docker+artifactory
In particular, remember to allow ports 8081 and 8082 on your aws ec2 security group.

### Resolving artifacts through Artifactory
In maven 3.9 we need https for artifactory to include these in our maven pom.
We first need to request an ssl certificate.
We will follow the instructions here:
https://levelup.gitconnected.com/adding-a-custom-domain-and-ssl-to-aws-ec2-a2eca296facd

The SSL certificate does take a few minutes to create so we do need to be patient:
![image](https://user-images.githubusercontent.com/27693622/227237035-38fae4f1-9902-4a79-b634-c41d6d4aef9c.png)

Now the certificate is showing as issued:
![image](https://user-images.githubusercontent.com/27693622/227237878-2550434c-adc0-4db5-9491-cc02f9d3c4bd.png)

We can now create a target group. I have a few target groups already:
![image](https://user-images.githubusercontent.com/27693622/227238416-7e8e9c24-9bf1-4b94-a3dc-0dd26005f036.png)

We want to create one for our EC2 instance.
We then register our instance to the target group:
![image](https://user-images.githubusercontent.com/27693622/227239645-d8f0b141-26db-4975-8c01-9e45f0c710f2.png)

Next we need to create an application load balancer. We will choose the application load balancer.
We also need to set up a target group as described in the article above.
I have set up https and a DNS for the artifactory instance because we need to use
https with maven3. I added the following reverse proxy to ```/etc/apache2/sites-available``` on the ec2 ubuntu linux instance:
```bash
        ServerName jfrog.drspencer.io
        ProxyPass / http://localhost:8082/
        ProxyPassReverse / http://localhost:8082/
```
using Apache3.

Now we have working artifactory instance on https://jfrog.drspencer.io:
![image](https://user-images.githubusercontent.com/27693622/227302591-05513572-9618-4cfe-90c6-4f98fbb01d80.png)

We can now use the correct settings in our settings.xml to deploy the jar:
```bash
Uploading to central: https://jfrog.drspencer.io/artifactory/libs-release-local/guru/springframework/spring-core-devops/0.0.3/spring-core-devops-0.0.3.jar
Uploaded to central: https://jfrog.drspencer.io/artifactory/libs-release-local/guru/springframework/spring-core-devops/0.0.3/spring-core-devops-0.0.3.jar (63 MB at 2.0 MB/s)
Uploading to central: https://jfrog.drspencer.io/artifactory/libs-release-local/guru/springframework/spring-core-devops/0.0.3/spring-core-devops-0.0.3.pom
Uploaded to central: https://jfrog.drspencer.io/artifactory/libs-release-local/guru/springframework/spring-core-devops/0.0.3/spring-core-devops-0.0.3.pom (3.9 kB at 6.2 kB/s)
Downloading from central: https://jfrog.drspencer.io/artifactory/libs-release-local/guru/springframework/spring-core-devops/maven-metadata.xml
Downloaded from central: https://jfrog.drspencer.io/artifactory/libs-release-local/guru/springframework/spring-core-devops/maven-metadata.xml (393 B at 1.5 kB/s)
Uploading to central: https://jfrog.drspencer.io/artifactory/libs-release-local/guru/springframework/spring-core-devops/maven-metadata.xml
Uploaded to central: https://jfrog.drspencer.io/artifactory/libs-release-local/guru/springframework/spring-core-devops/maven-metadata.xml (345 B at 559 B/s)
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  35.186 s
[INFO] Finished at: 2023-03-24T14:02:35Z
[INFO] ------------------------------------------------------------------------

```

We now see the release on our artifactory repository browser:
<img width="506" alt="image" src="https://user-images.githubusercontent.com/27693622/227556194-db0dfd0e-b99a-4bc6-ac85-dce2191208af.png">

### Jenkins with Artifactory
We now set up Jenkins to look at the maven home directory to get changes uploaded to artifactory.
We go to our operating system's home directory for jenkins:
```bash
/var/lib/jenkins
```
We first log into our ec2 instance and then change to jenkins user:
```bash
[ec2-user@ip-172-31-91-7 ~]$ sudo su -s /bin/bash jenkins
bash-5.2$ 
bash-5.2$ whoami
jenkins
```

We then add the same configuration from our settings.xml to /var/lib/jenkins/settings.xml. Now we return to jenkins and add a build.
We can now see that we are using our artifactory instance to download the required dependencies:
<img width="1235" alt="image" src="https://user-images.githubusercontent.com/27693622/227561234-920779af-9a33-408b-9147-225e0a32e44e.png">

Artifactory is important so that we can host the jars that we are building. It also works as read cache to save dependencies.
Other tools on Artifactory such as Xray can help ensure that we are careful with open source software and have a history of what we
are using. Xray checks dependencies for security issues.

### Virtualised cloud deployment
We will now set up a docker container for our mysql database. We will then deploy our application. 
We will create a new ec2 instance and install docker with the same commands as before. We will then
add mysql with the following command:
```bash
sudo docker run -d --name mysql -p 3306:3306 -v /home/ec2-user/mysql_data:/var/lib/mysql -e MYSQL_ROOT_PASSWORD=password mysql:latest
```
We can now check our running docker container and connect to mysql:
```bash
# check running instances
sudo docker ps

# connect to container
sudo docker exec -it mysql bash

# connect to MySQL
mysql -p

# create database
CREATE DATABASE springguru;

create user 'spring_guru_owner'@'localhost' identified with mysq_native_password by 'password';
GRANT ALL PRIVILEGES ON springguru.* to 'spring_guru_owner'@'localhost';
```
Here we are connecting to mysql and creating the springguru database. We then create a new user account for interacting with the
springguru database. This is not appropriate on a production database. In a major application the application would not be
able to update the schema.

### ec2 Docker application
We can run the application in the command line with properties so it is better to run with a file that can be kept on the
ec2 instance. We can set up a service with an external application.properties file on the operating system.
We put an application.properties file the same directory as the jar.
```bash
[ec2-user@ip-172-31-83-176 ~]$ ls
amazon-corretto-11-x64-linux-jdk.tar.gz  application.properties
amazon-corretto-11.0.18.10.1-linux-x64   spring-core-devops-0.0.3.jar
```
These are the contents:
```properties
spring.datasource.url=jdbc:mysql://54.82.50.187:3306/springguru
spring.datasource.username=spring_guru_owner
spring.datasource.password=GuruPassword
spring.jpa.hibernate.ddl-auto=update

```
If we wanted to add the same as environment variables we would do the following:
```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://54.82.50.187:3306/springguru
export SPRING_DATASOURCE_USERNAME=spring_guru_owner
export SPRING_DATASOURCE_PASSWORD=GuruPassword
```
We would then run the application with:
```bash
java -jar ./spring-core-devops-0.0.3.jar
```
Instead we are going to run the application with systemd to ensure we keep environment variables each time.

### Running Spring ec2 with persistent env

We then add a service file on the spring boot ec2 instance:
1. change to root and go to the system file on the linux instance
```bash
sudo su
cd /etc/systemd/system
```
Save the following to spring.service:
```bash

[Unit]
Description=Spring Boot Service
After=syslog.target

[Service]
User=ec2-user
# set di to location of application.properties and springboot jar
WorkingDirectory=/home/ec2-user
ExecStart=/usr/bin/java -jar spring-core-devops-0.0.3.jar
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```
The above is in /etc to run services. We run the jar as ec2-user and tell the application where java is
and run the jar file.

2. Reload service definitions: 
```bash
systemctl daemon-reload 
```
3. Add start on boot:
```bash
systemctl enable springboot.service
```
4. Start the application:
```bash
systemctl start springboot
```

We can now see the service is running:
```bash
[root@ip-172-31-83-176 system]# ps -ef | grep java
ec2-user   29225       1 99 12:21 ?        00:00:12 /bin/java -jar spring-core-devops-0.0.3.jar
root       29250   28901  0 12:21 pts/1    00:00:00 grep --color=auto java
```

In order to view the spring logs we can run the following:
```bash
tail -f /var/log/messages
```

If we want to remove services we can run the following:
```bash

systemctl stop spring
systemctl disable spring
rm /etc/systemd/system/spring
rm /etc/systemd/system/spring # and symlinks that might be related
rm /usr/lib/systemd/system/spring 
rm /usr/lib/systemd/system/spring # and symlinks that might be related
systemctl daemon-reload
systemctl reset-failed
```
We can check the systemctl services running:
```bash
systemctl | grep spring
```
We can see the logs for the service:
```bash
journalctl -u service-name.service
```
You can also stop overrun for the logs with ```less```.
You 

We can see the application running at the public address for the ec2 instance (port 8080):
![image](https://user-images.githubusercontent.com/27693622/227719004-83520eb4-97b8-4d52-aa8d-3cfb31e1e602.png)

We can also view logs for a certain time frame:
```bash
journalctl -u springboot.service --since "2023-03-25 13:36:00" | less
```

### Amazon RDS

We are now going to get our feet wet with Amazon RDS. We will use RDS to manage our mysql database.
Amazon backs up the database, applies patches and allows us to offload the management of the database to Amazon.
Amazon RDS allows us not to have to manage the database administration. AWS also helps us with security and patching.
We will provision an Amazon database on RDS and then update our configuration for our application to use the RDS database.
We will leverage the properties for the new database. Everything should work the same.

#### Provision Mysql database on RDS
We will start by provisioning a database on Amazon RDS.

![image](https://user-images.githubusercontent.com/27693622/227983403-7d5e3e2f-7cac-4991-8af0-019cc3315407.png)

This takes a while to set up the database:
![image](https://user-images.githubusercontent.com/27693622/227985399-b48aff81-0b1a-444c-8bf3-fbd4208e4efb.png)

We can now connect to the database using the following configuration:

```properties

guru.springframework.profile.message=This is mysql rds Profile
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

spring.datasource.url=jdbc:mysql://<YOUR MYSQL INSTANCE URL>:3306/springgurudb

spring.jpa.hibernate.ddl-auto=update
spring.datasource.username=spring
spring.datasource.password=password
```

Our application is now running against the rds database locally.

We now deploy our application using artifactory and then pull the new jar onto our ec2 instance.
![image](https://user-images.githubusercontent.com/27693622/228001418-ebfd5241-3aa8-4438-84c0-a96ef80527b9.png)

We can now pull the jar to our ec2 instance using wget:
```bash
sudo wget --user=tom --password=AKCp8nzqSiJFSBYhrCe4q14oDSSsJUH7dhivt4q2Y8ym8igR3fRxdJ3tGSnebXX68aq6CfvKM https://jfrog.drspencer.io/artifactory/libs-release-local/guru/springframework/spring-core-devops/0.0.4/spring-core-devops-0.0.4.jar
```

We then run the application jar:
```bash
java -jar spring-core-devops-0.0.4.jar
```

