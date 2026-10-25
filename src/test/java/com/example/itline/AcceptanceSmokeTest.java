/* =========================================================================== 
*  Приёмочные smoke-тесты. 
*  В данном файле можно изменить только пакет. 
* =========================================================================== 
*/  
package com.example.itline;    
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AcceptanceSmokeTest {        

    @Autowired      
    private ApplicationContext applicationContext;        
    
    @Autowired      
    private EntityManagerFactory entityManagerFactory;        
    
    @Autowired      
    private DataSource dataSource;        
    
    @LocalServerPort      
    private int port;        
    
    /** 1. Контекст Spring поднимается. */      
    @Test      
    void contextLoads() {          
        assertNotNull(applicationContext, "Контекст Spring не поднялся");          
        assertNotNull(entityManagerFactory,                  
            "JPA не поднялся: проверьте настройки spring.jpa и доступность БД"
        );      
    }        
        
    /* 2. Приложение подключается к БД. */      
    @Test      
    void databaseIsReachable() throws Exception {          
        try (var connection = dataSource.getConnection()) {              
            assertTrue(connection.isValid(5), "Соединение с БД недействительно");          
        }      
    }      
    
    /* 3. GET /itline возвращает 200. */      
    @Test      
    void itlinePageReturns200() throws Exception {          
        HttpClient client = HttpClient.newBuilder()                  
            .connectTimeout(Duration.ofSeconds(10))                  
            .build();          
        HttpRequest request = HttpRequest.newBuilder()                  
            .uri(URI.create("http://localhost:" + port + "/itline"))                  
            .timeout(Duration.ofSeconds(30))  
            .GET()  
            .build();  
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());  
            assertEquals(200, response.statusCode(),  
                "GET /itline вернул " + response.statusCode() + " вместо 200 (требования к интерфейсу)"
            );  
            assertFalse(response.body() == null || response.body().isBlank(),  
                "GET /itline вернул пустую страницу"
            );  
    } 
}
