
# Lambda Layers
A layer is a zip archive that contains libraries, custom runtime or other dependencies that can be attached to a Lambda function: [docs](https://docs.aws.amazon.com/lambda/latest/dg/configuration-layers.html). With layers, libraries can be used with lambdas without the need to include them in the deployment package.
I will be testing Lamba layers using java and maven. The spanish version of this text is available [here](https://github.com/sbernal93/lambda-layer-test/blob/master/README_ES.md)

## Layers with Maven
To add a dependency to a Lambda layer, we will follow these steps:

 1. Create the dependency
 2. Create an uber-jar of the dependency, ex: using maven-shade-plugin
 3. Deploy the Lambda layer
 4. Create the Lambda function
 5. Add the dependency 
 6. Deploy the Lambda and attach the layer

### Layer Creation
First we create the dependency, for this, we will write a simple class that prints a special message.
```
public class LayerUtil {
    
    /**
     * Method that prints a special string 
     * @param str
     */
    public static final void printMethod(String str) {
        System.out.println("This is a message from a layer!");
        System.out.println("The message is: [" + str + "]");
    }

}
```
We configure the pom to create an uber-jar so that the dependency includes all its necessary dependencies when packaging it so it can be used without the Lambdas having to add additional imports or use other layers. For now, we will configure the ``maven-shade-plugin`` in the pom which allows us to do just that:
```
	<build>
		<plugins>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-shade-plugin</artifactId>
				<version>2.3</version>
				<configuration>
					<createDependencyReducedPom>false</createDependencyReducedPom>
				</configuration>
				<executions>
					<execution>
						<phase>package</phase>
						<goals>
							<goal>shade</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>
```
Once we have created the dependency, we package it with ``mvn clean package``. To be able to use the generated .jar in Lambda layers it needs to be archived into a .zip and placed in an specific folder, so that the final .zip file has the following format:
```
+-- lambda-layer-test-dependency.zip
|   +-- java
|	|	+-- lib
|	|	|	+-- lambda-layer-test-dependency-0.0.1-SNAPSHOT.jar
```
So, we create manually a folder called java/lib and place the .jar in it, we then compress the folder into a .zip with the name of the dependency and we upload this to a new Layer, using the AWS console:
![Upload Lamba Layer](https://imgur.com/n4kq8Zc.png)

### Lambda Creation
Having the Layer created, we proceed by creating the Lambda function.
The first thing to do now is import the dependency we created previously, taking into account that the main idea with Lambda Layer is to reduce the size of the deployment package of the Lambda functions, we need to make sure that the Layer dependency is not included in the Lambda package. For this, we can use the ``<scope>`` option provided by maven and set it to ``<provided>``, so in this way we can reference it and use it locally, but when packaging the Lambda, maven won't include it in the final .jar, but it will instead search for it at runtime, in our case it will find it in the Layer.

With this, and including the ``maven-shade-plugin`` as before, the resulting pom will look something like the following:
```
      <dependencies>
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-lambda-java-core</artifactId>
            <version>1.2.0</version>
        </dependency>
        <dependency>
        	<groupId>com.sbernal93</groupId>
        	<artifactId>lambda-layer-test-dependency</artifactId>
        	<version>0.0.1-SNAPSHOT</version>
        	<scope>provided</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>2.3</version>
                <configuration>
                    <createDependencyReducedPom>false</createDependencyReducedPom>
                </configuration>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
```

We then create a simple Handler that calls the method we created in the layer:
```
public class MainHandler implements RequestHandler<HashMap<String, Object>, Object>{

    public Object handleRequest(HashMap<String, Object> input, Context context) {
        System.out.println("Lambda invoked, using layer function");
        LayerUtil.printMethod("test");
        return null;
    }

}
```
We package the lambda running ``mvn clean package`` and we upload the code and create a new Lambda function using the AWS console:
![Upload lambda](https://imgur.com/mN4Ig0T.png)

### Testing
Without attaching the Layer to the Lambda function, the function should result in error since it will not be able to find the dependency, so we first want to test this behavior to make sure that the Lambda Layer is working as expected, and that we did not package the dependency with the Lambda function. 

We run this simple test with some dummy input and we get the following error:
![test lambda without layer](https://imgur.com/4pR1Go4.png)

Great! That's what we were expecting, the Lambda is not able to find the created class that is in the Layer. 
So now we attach the Layer using the AWS console:
![Attach layer to lambda](https://imgur.com/rv44Rhm.png)

If we run the Lambda function now, everything works fine and the message is shown on the log output :![Corrida exitosa](https://imgur.com/drFc9pf.png)

## Improvements

In the previous tests we used ``maven-shade-plugin`` to be able to create an uber-jar of the dependency, but, Lambda Layers expects the jar to be in a specific location, so we had to manually create the .zip file necessary to upload. Ideally, we want this to be generated automatically when we run the maven command, so we prevent errors and for future automated deployments. For this we can use a maven assembly descriptor which we can configure to create this .zip file:
```
<assembly 
    xmlns="http://maven.apache.org/plugins/maven-assembly-plugin/assembly/1.1.3" 
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
    xsi:schemaLocation="http://maven.apache.org/plugins/maven-assembly-plugin/assembly/1.1.3 http://maven.apache.org/xsd/assembly-1.1.3.xsd"> 
    <id>package-assembly</id> 
    <formats> 
        <format>zip</format> 
    </formats> 
    <includeBaseDirectory>false</includeBaseDirectory> 
    <fileSets> 
        <fileSet> 
            <directory>${project.build.directory}</directory> 
            <outputDirectory>java/lib</outputDirectory> 
            <includes> 
                <include>**/*.jar</include> 
            </includes> 
            <excludes>
            	<exclude>**/original*</exclude>
            </excludes>
        </fileSet> 
    </fileSets> 
</assembly> 
```
The ``<format>`` is set to .zip and the ``<outputDirectory>`` is the directory where we want the output to be placed. We then include the generated jar with the ``<include>`` but we also ``<exclude>`` the "original-*.jar" which is created by the ``maven-shade-plugin``

With this, we reference it in the pom file, leaving the ``maven-shade-plugin``. We placed the assembly-descriptor file in ``src/main/resources``, so we set this in the ``maven-assembly`` option of the pom file:
 
 ```
	<build>
		<plugins>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-shade-plugin</artifactId>
				<version>2.3</version>
				<configuration>
					<createDependencyReducedPom>false</createDependencyReducedPom>
				</configuration>
				<executions>
					<execution>
						<phase>package</phase>
						<goals>
							<goal>shade</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
			<plugin>
				<artifactId>maven-assembly-plugin</artifactId>
				<version>3.0.0</version>
				<configuration>
					<appendAssemblyId>false</appendAssemblyId>
					<finalName>${project.artifactId}</finalName>
					<descriptors>
						<descriptor>src/main/resources/assemblies/package-assembly.xml</descriptor>
					</descriptors>
				</configuration>
				<executions>
					<execution>
						<id>pack</id>
						<phase>package</phase>
						<goals>
							<goal>single</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>
```

Now, when we run ``mvn clean package`` we have a .zip file with the desired format. This will save us some time and possible mistakes.

## Updating the Layer
Now we want to see what happens if we update the Layer, but no the Lambda function code. So for this, we update our dependencies version from ``0.0.1-SNAPSHOT`` to ``0.0.2-SNAPSHOT`` using the pom. Then we edit the code a bit, so we can tell what version the Lambda is using, so we change the message output in the dependency:
```
    public static final void printMethod(String str) {
        System.out.println("This is a message from a layer with a different version!");
        System.out.println("The message is: [" + str + "]");
    }
```
We package this dependency, and, if the assembly descriptor was configured correctly, we can upload it to Lambda Layer. The create .jar file should have the ``0.0.2-SNAPSHOT``, and we release a new Layer version using this. 
We then proceed to the Lambda function, and without changing the code, we remove the previous Layer version, and associate the new Layer version. When executing the Lambda, it updates with the new Layer version:
![Succesful run](https://imgur.com/VuoIGnH.png)

## Conclusion
We now have a Lambda running with a dependency in a Lambda Layer. Even though this was a small test, with a dependency of a few KBs, in real cases some dependencies can make the Lambda function code way more heavy, and considering these dependencies are spread through maybe hundreds of Lambda functions, the impact on deployment speed is noticeable. 

Also, here is a [nice post](https://medium.com/consulner/performance-of-aws-lambda-with-and-without-layers-9bffbb5434f3) about cold and hot start times with/without Lambda layers 

When updating a Layer, the Lambda dependency is updated without having to modify the code directly. 
This has some advantages:

 - Simple way to update dependencies without having to modify the Lambda directly, specially in cases where there is small changes in the dependency code. 
 - Specially advantageous when having to update a large amounts of Lambdas (imagine having to manually change the dependency version of 100s of lambdas just because of a minor change) this can be even simpler if a IaC tool is being used. 

Things to consider before using Layers:

 - Managing dependencies inside the Lambda code also has some benefits, since you can create unit tests and make sure that changes to version don't break the current code, if relying solely on Layers, integration tests are heavily recommended. This way you can run some automated tests that execute the lambdas and test their behaviors and report any issues after updating the Layer versions. 
 - Lambda code may be outdated or not in tune with the code that's actually running, this can be problematic when the Lambda code must be updated. 
 - It is also recommended to define a good API for the dependencies so that you can take real advantage of the benefits of Lambda Layers and not break the Lambda functions on every Layer update. 

**Important Note:** AWS currently only allows a maximum of 5 Layers attached to a Lambda function, so take this into consideration when designing a solution with Lambda Layers


### Possible Improvements and Next Steps
Things I will test later on if I find the time:

 - Create assembly descriptor as separate project for re-usability 
 - Use IaC tool for deploying the Layer on multiple Lambda

### Repos
The code used is available in the following locations:
 - [Dependency](https://github.com/sbernal93/lambda-layer-test/tree/master/lambda-layer-test-dependency)
 - [Lambda](https://github.com/sbernal93/lambda-layer-test/tree/master/lambda-layer-test)

