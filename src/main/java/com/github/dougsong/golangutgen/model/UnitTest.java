package com.github.dougsong.golangutgen.model;

import java.util.ArrayList;
import java.util.List;

public class UnitTest {
    private String testFuncName;
    private String funcName;
    private List<Arg> args;
    private List<Arg> wants;

    public UnitTest() {
        this.args = new ArrayList<>();
        this.wants = new ArrayList<>();
    }

    public String getFuncName() {
        return funcName;
    }

    public void setFuncName(String funcName) {
        this.funcName = funcName;
    }

    public List<Arg> getArgs() {
        return args;
    }

    public void setArgs(List<Arg> args) {
        this.args = args;
    }

    public List<Arg> getWants() {
        return wants;
    }

    public void setWants(List<Arg> wants) {
        this.wants = wants;
    }

    public String getTestFuncName() {
        return testFuncName;
    }

    public void setTestFuncName(String testFuncName) {
        this.testFuncName = testFuncName;
    }
}
