package com.ggg.demo;

import com.ggg.annotation.GGGController;
import com.ggg.annotation.GGGRequestMapping;

@GGGController
@GGGRequestMapping("/class")
public class DemoAction {

    @GGGRequestMapping("/method1")
    public void firstTest(){
        System.out.println("moths....");
    }
}
