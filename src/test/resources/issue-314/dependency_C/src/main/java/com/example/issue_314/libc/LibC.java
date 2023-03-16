package com.example.issue_314.libc;

import com.example.issue_314.libd.LibD;

public class LibC {
    private LibC() {}

    public static void libCMethod() {
        System.out.println("In libC");
        LibD.libDMethod();
    }
}