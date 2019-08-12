
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
![Subida Lamba Layer](https://imgur.com/n4kq8Zc)

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
![Subida lambda](https://imgur.com/mN4Ig0T)

### Prueba
Sin juntar el Layer al Lambda, el Lambda deberia fallar ya que no encuentra la dependencia. Haremos esta prueba para comprobar que esta efectivamente buscando la dependencia correctamente en el Layer y no esta empaquetada.
Al correr el lambda con un input cualquiera, nos lanza el siguiente error:
![Prueba lambda sin layer](https://imgur.com/4pR1Go4)

Ahora le agregamos el layer:
![Agregar layer a lambda](https://imgur.com/rv44Rhm)

Ahora si corremos el lambda, funciona perfecto:
![Corrida exitosa](https://imgur.com/drFc9pf)

## Conclusión
Y asi tenemos un lambda corriendo con una dependencia en Lambda layer. Si bien esta prueba es pequeña con una dependencia de pocos KBs, en casos reales, las dependencias pueden hacer que el lambda pese mas de 20MBs, con esto podemos reducir los tamaños de los paquetes separando dependencias que son usadas por diferentes lambdas
**Importante:** si bien con lambda layer reducimos el tamaño del paquete, actualmente solo se pueden poner 5 layers como maximo para un Lambda, este limite se debe tomar en consideracion para cualquier diseño que involucre Lambdas y Layers

### Mejoras
Como mejoras se pueden realizar lo siguiente:

 - La dependencia se movio manual a una carpeta y se creo el zip para probar, buscar para que maven cree el paquete directamente como queremos para poder subir a layer
 - Probar actualizar el layer y hacer que actualice la dependencia, sin tener que actualizar el lambda.
 - 
### Repos
El codigo utilizado esta disponible en los siguientes repos:
 - [Dependencia](https://github.com/sbernal93/lambda-layer-test-dependency)
 - [Lambda](https://github.com/sbernal93/lambda-layer-test)

