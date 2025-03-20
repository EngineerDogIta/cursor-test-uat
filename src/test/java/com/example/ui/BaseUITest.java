package com.example.ui;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseUITest {
    
    protected WebDriver driver;
    
    @LocalServerPort
    protected int port;
    
    @BeforeEach
    public void setup() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless"); // Esegui in modalità headless
        driver = new ChromeDriver(options);
    }
    
    @AfterEach
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
    
    protected String getBaseUrl() {
        return "http://localhost:" + port;
    }
} 