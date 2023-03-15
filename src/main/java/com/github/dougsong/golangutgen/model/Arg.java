package com.github.dougsong.golangutgen.model;

public class Arg {
    private String name;
    private String type;

    private boolean basicType;

    public boolean getBasicType() {
        return basicType;
    }

    public void setBasicType(boolean basicType) {
        this.basicType = basicType;
    }

    public Arg(String name, String type, boolean basicType) {
        this.name = name;
        this.type = type;
        this.basicType = basicType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
