package com.example.issue_314.liba;

import com.example.issue_314.libb.LibB;
import com.example.issue_314.libc.LibC;

public class LibA {
    private LibA() {}

    public static void main(final String[] args) {
        System.out.println("In libA");
        LibB.libBMethod();
        try {
            LibC.libCMethod();
        } catch (final NoClassDefFoundError ncdfe) {
            System.out.println("Optional library libC not present on classpath");
        }
    }
}