
# Lambda Layers
Un layer es un archive zip que contiene librerías, un runtime customizado u otras dependencias, que se pueden adjuntar a una función lambda. Con layers se pueden usar librerías en los lambdas sin la necesidad de empaquetarlas con el paquete de deployment. 

## Layers con Maven
Para trabajar una dependencia como una lambda layer, se necesitan seguir los siguientes pasos:

 1. Crear la librería
 2. Crear un paquete usando como un uber-jar, ej: usando maven-shade-plugin
 3. Deploy del lambda layer
 4. Crear lambda
 5. Agregar dependencia del paquete
 6. Deployar lambda y attachear el layer

### Creación del layer

Primero creamos la libreria, para esto vamos a crear una simple dependencia que pueda escribir en los logs
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
Configuramos el pom para crear un uber-jar, la idea es que el layer tenga todas las dependencias necesarias para poder usarla sin tener que el lambda traer dependencias adicionales o que tenga que usar otros layers. Para esto usamos ``maven-shade-plugin`` en el pom para que nos facilite esta funcionalidad:
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
Una vez tenemos la dependencia creada, la empaquetamos con ``mvn clean package``. Teniendo el .jar, para poder ser entendida por el lambda, la dependencia necesita estar dentro de un zip, y el jar en una carpeta especifica: 
```
+-- lambda-layer-test-dependency.zip
|   +-- java
|	|	+-- lib
|	|	|	+-- lambda-layer-test-dependency-0.0.1-SNAPSHOT.jar
```
Así que creamos una carpeta manual que sea java/lib y ponemos el .jar dentro, y luego lo comprimimos en .zip con el nombre de la dependencia y subimos ese .zip a un Layer:
![Subida Lamba Layer](https://imgur.com/n4kq8Zc.png)

### Creación del Lambda
Teniendo el layer creado, procedemos con la creación del lambda. 
Lo primero que debemos hacer ahora es importar la dependencia que creamos anteriormente, tomando en cuenta que la idea principal de los Lambda Layer es reducir el tamaño del paquete que se deployea en los lambdas, para lo cual debemos asegurar de que la dependencia que subimos al Layer no se empaquete con el lambda. Para esto usamos el ``<scope>`` de maven puesto en ``provided``, de esta forma lo podemos referenciar y usar de forma local pero al empaquetarlo no lo incluye, sino que lo busca en el runtime, por lo que lo buscará en el layer cuando hagamos el deploy.

El pom quedaria de la siguiente forma, incluyendo también el ``maven-shade-plugin``:
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

Luego, creamos un Handler sencillo que llame al método creado en el layer:

```
public class MainHandler implements RequestHandler<HashMap<String, Object>, Object>{

    public Object handleRequest(HashMap<String, Object> input, Context context) {
        System.out.println("Lambda invoked, using layer function");
        LayerUtil.printMethod("test");
        return null;
    }

}
```
Empaquetamos el lambda corriendo ``mvn clean package`` (**nota**: si no resuelve la dependencia que creamos, intenta empaquetar la dependencia con ``mvn clean install``) y creamos un nuevo lambda subiendo el código creado:
![Subida lambda](https://imgur.com/mN4Ig0T.png)

### Prueba
Sin juntar el Layer al Lambda, el Lambda deberia fallar ya que no encuentra la dependencia. Haremos esta prueba para comprobar que esta efectivamente buscando la dependencia correctamente en el Layer y no esta empaquetada.
Al correr el lambda con un input cualquiera, nos lanza el siguiente error:
![Prueba lambda sin layer](https://imgur.com/4pR1Go4.png)

Ahora le agregamos el layer:
![Agregar layer a lambda](https://imgur.com/rv44Rhm.png)

Ahora si corremos el lambda, funciona perfecto:
![Corrida exitosa](https://imgur.com/drFc9pf.png)

## Mejoras

En la prueba inicial, usamos ``maven-shade-plugin`` para poder crear un uber-jar de la dependencia, sin embargo, Lambda Layers espera el jar de una forma especifica, para la cual tuvimos que manualmente crear el .zip necesario para subirlo. Lo ideal seria que esto se genere automaticamente. Para esto podemos usar un assembly descriptor de maven que nos haga este trabajo, y asi al empaquetarlo ya lo tenemos de la manera que buscamos. Para esto creamos el assembly descriptor de la siguiente manera:

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
El formato se especifica en ``<format>`` como .zip y en el ``<outputDirectory>`` el directorio que queremos que se ponga el output. Luego incluimos el jar que queremos con el ``<include>`` y excluimos el "original-*.jar" que es creado por el ``maven-shade-plugin`` en el ``<exclude>``.
 
Teniendo esto lo referenciamos en el pom, dejando todavía el plugin de ``maven-shade-plugin``. Como lo colocamos en la carpeta de ``src/main/resources``, apuntamos el ``maven-assembly`` a que busque el descriptor ahí:
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

Ahora al correr ``mvn clean package`` tenemos el .zip con el formato deseado. Esto nos ahorra tiempo de trabajo manual y posible errores. 

## Actualización del Layer

Ahora vamos a ver que pasa una vez actualizamos la dependencia, y creamos una nueva versión del Layer y se la ponemos al Lambda.
Primero, actualizamos la versión de la dependencia de ``0.0.1-SNAPSHOT`` a ``0.0.2-SNAPSHOT`` en el pom. Luego editamos un poco el código para notar que versión usa, asi que cambiamos lo que imprime el método que habíamos creado anteriormente:
```
    public static final void printMethod(String str) {
        System.out.println("This is a message from a layer with a different version!");
        System.out.println("The message is: [" + str + "]");
    }
```
Empaquetamos la dependencia y la subimos a Lambda Layer y creamos una nueva versión, el jar generado contiene la versión ``0.0.2-SNAPSHOT``. 
Vamos a nuestro lambda, y sin cambiar el código, le quitamos la versión anterior del Layer, y le asociamos la versión nueva del Layer. Al volver a ejecutar el Lambda, este toma los cambios correctamente:
![Corrida exitosa](https://imgur.com/VuoIGnH.png)

## Conclusión
Y asi tenemos un lambda corriendo con una dependencia en Lambda layer. Si bien esta prueba es pequeña con una dependencia de pocos KBs, en casos reales, las dependencias pueden hacer que el lambda pese mas de 20MBs, con esto podemos reducir los tamaños de los paquetes separando dependencias que son usadas por diferentes lambdas.
Al actualizar el Layer, se actualiza la dependencia del Lambda sin tener que modificar directamente el código del Lambda. 
Ventajas de esto:
 - Actualización de dependencias de manera sencilla sin tener que modificar el Lambda
 - Actualización simultanea de muchos lambdas al actualizar su Layer (especialmente si se usa una herramienta de IaC)
 
Posibles desventajas o casos a contemplar:
 - Puede traer problemas de incompatibilidad si los cambios no se prueban primero, especialmente para procesos que incluyan muchos lambdas. Se puede disminuir el riesgo si se realizan pruebas de integración automatizadas
 - El codigo del lambda puede quedar desactualizado en dessintonia con lo que esta deployado. Esto puede causar riesgos cuando se deba actualizar el lambda. 

**Importante:** si bien con lambda layer reducimos el tamaño del paquete, actualmente solo se pueden poner 5 layers como maximo para un Lambda, este limite se debe tomar en consideración para cualquier diseño que involucre Lambdas y Layers

### Mejoras
Como mejoras se pueden realizar lo siguiente:

 - El assembly descriptor se creo dentro del proyecto de lambda-layer-test-dependency, podria ser creado como un proyecto aparte y ser reutilizado por otras dependencias
 
### Repos
El codigo utilizado esta disponible en los siguientes repos:
 - [Dependencia](https://github.com/sbernal93/lambda-layer-test-dependency)
 - [Lambda](https://github.com/sbernal93/lambda-layer-test)

