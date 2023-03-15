package com.github.dougsong.golangutgen.actions;

import com.github.dougsong.golangutgen.model.Arg;
import com.github.dougsong.golangutgen.model.UnitTest;
import com.goide.psi.*;
import com.goide.psi.impl.GoFunctionDeclarationImpl;
import com.goide.psi.impl.GoMethodDeclarationImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URL;
import java.util.*;

public class GenUTAction extends AnAction {

    private static final Logger LOGGER = Logger.getInstance(GenUTAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        List<UnitTest> utList = new ArrayList<>();
        psiFile.accept(new GoRecursiveVisitor() {
            @Override
            public void visitFunctionOrMethodDeclaration(@NotNull GoFunctionOrMethodDeclaration o) {
                // function: public, ex. func Xxxxx
                // method: belongs to struct, ex. func (xx Xxx) Xxx
                super.visitFunctionOrMethodDeclaration(o);
                UnitTest unitTest = generateUnitTest(o);
                if (unitTest == null) {
                    LOGGER.error("unit test is null");
                } else {
                    utList.add(unitTest);
                }
            }
        });

        String filePath = psiFile.getOriginalFile().getVirtualFile().getPath();
        if (!filePath.endsWith(".go")) {
            LOGGER.error(String.format("file path [%s] does not end with .go", filePath));
            return;
        }

        // generate unit test code
        String packageName = ((GoFile) psiFile).getPackage().getIdentifier().getText();
        String testFilePath = String.format("%s_test.go", filePath.substring(0, filePath.length() - 3));
        try {
            generateTestTemplate(testFilePath, packageName, utList);
        } catch (IOException | TemplateException ex) {
            LOGGER.error("generate template error");
            throw new RuntimeException(ex);
        }

        // import and fmt
        try {
            Runtime.getRuntime().exec(new String[]{"bin/sh", "goimports", "-w", testFilePath});
            Runtime.getRuntime().exec(new String[]{"bin/sh", "go", "fmt", testFilePath});
        } catch (Exception ex) {
            LOGGER.error(ex);
        }
    }

    private UnitTest generateUnitTest(GoFunctionOrMethodDeclaration o) {
        if (o instanceof GoMethodDeclarationImpl) {
            return generateInterfaceFunc(o);
        } else if (o instanceof GoFunctionDeclarationImpl) {
            return generateNormalFunc(o);
        }
        throw new RuntimeException("invalid go function or method");
    }

    private UnitTest generateInterfaceFunc(GoFunctionOrMethodDeclaration o) {
        UnitTest unitTest = generateNormalFunc(o);
        if (unitTest == null) {
            return null;
        }
        unitTest.setInterfaceFunc(true);
        GoReceiver receiver = ((GoMethodDeclarationImpl) o).getReceiver();
        String argName = receiver.getName() == null ? "receiver" : receiver.getName();
        String argType = receiver.getType().getText();
        if (argType.startsWith("*")) {
            argType = argType.substring(1);
        }
        unitTest.setReceiver(new Arg(argName, argType, GOLANG_BASIC_TYPE.contains(argType)));
        return unitTest;
    }

    private UnitTest generateNormalFunc(GoFunctionOrMethodDeclaration o) {
        UnitTest ut = new UnitTest();

        // 1. func name
        String funcName = o.getIdentifier().getText();
        String testFuncName = funcName;
        if (Character.isLowerCase(testFuncName.charAt(0))) {
            testFuncName = String.format("%s%s", String.valueOf(testFuncName.charAt(0)).toUpperCase(), testFuncName.substring(1));
        }
        ut.setFuncName(funcName);
        ut.setTestFuncName(testFuncName);
        GoSignature signature = o.getSignature();
        if (signature == null) {
            return null;
        }

        // 2. request parameter
        for (GoParameterDeclaration goParameterDeclaration : signature.getParameters().getParameterDeclarationList()) {
            String paramType = goParameterDeclaration.getType().getText();
            for (GoParamDefinition goParamDefinition : goParameterDeclaration.getParamDefinitionList()) {
                // multi request parameter with single type define. ex. (id, accountId, groupId string)
                ut.getArgs().add(new Arg(goParamDefinition.getText(), paramType, GOLANG_BASIC_TYPE.contains(paramType)));
            }
        }

        // 3. result parameter
        GoResult result = signature.getResult();
        if (result != null) {
            GoParameters parameters = result.getParameters();
            if (parameters != null) {
                List<GoParameterDeclaration> resultParameters = parameters.getParameterDeclarationList();
                for (int i = 0; i < resultParameters.size(); i++) {
                    GoParameterDeclaration goParameterDeclaration = resultParameters.get(i);
                    List<GoParamDefinition> paramDefinitionList = goParameterDeclaration.getParamDefinitionList();
                    String resultType = goParameterDeclaration.getType().getText();
                    boolean basicType = GOLANG_BASIC_TYPE.contains(resultType);
                    if (RESULT_TYPE_NAME_MAP.containsKey(resultType)) {
                        // 通用参数映射，如error -> err
                        ut.getWants().add(new Arg(RESULT_TYPE_NAME_MAP.get(resultType), resultType, basicType));
                    } else {
                        if (paramDefinitionList.isEmpty()) {
                            ut.getWants().add(new Arg(String.format("ret_%d", i), resultType, basicType));
                        } else {
                            ut.getWants().add(new Arg(paramDefinitionList.get(0).getText(), resultType, basicType));
                        }
                    }
                }
            } else {
                // single param with no alias
                String resultType = result.getType().getText();
                String argName = RESULT_TYPE_NAME_MAP.getOrDefault(resultType, "ret");
                ut.getWants().add(new Arg(argName, resultType, GOLANG_BASIC_TYPE.contains(resultType)));
            }
        }

        return ut;
    }

    private void generateTestTemplate(String testFilePath, String packageName, List<UnitTest> unitTests) throws IOException, TemplateException {
        // TODO support customize template
        URL resource = this.getClass().getClassLoader().getResource("ut_template.ftl");
        if (resource == null) {
            LOGGER.error("ut template resource not found");
            return;
        }

        InputStream is = resource.openStream();
        StringBuilder sb = new StringBuilder();
        for (String line : IOUtils.readLines(is, "UTF-8")) {
            sb.append(line).append("\n");
        }

        String templateTmpFilePath = null;
        try {
            String templateContent = sb.toString();
            String tmpFileName = String.format("golang-ut-gen_ut-template_%s", UUID.randomUUID());
            templateTmpFilePath = String.format("/tmp/%s", tmpFileName);
            writeFile(templateTmpFilePath, templateContent);

            Configuration configuration = new Configuration();
            configuration.setDirectoryForTemplateLoading(new File("/tmp"));
            configuration.setDefaultEncoding("UTF-8");

            Template t = configuration.getTemplate(tmpFileName);

            Map<String, Object> map = new HashMap<>();
            map.put("packageName", packageName);
            map.put("unitTests", unitTests);

            Writer out = new FileWriter(testFilePath);
            t.process(map, out);
            out.flush();
            out.close();
        } finally {
            // delete tmp file
            if (templateTmpFilePath != null) {
                File file = new File(templateTmpFilePath);
                boolean delete = file.delete();
                LOGGER.info("tmp file delete " + delete);
            }
        }
    }

    private void writeFile(String tmpPath, String templateContent) throws IOException {
        BufferedWriter out = new BufferedWriter(new FileWriter(tmpPath));
        out.write(templateContent);
        out.close();
    }

    private static final Map<String, String> RESULT_TYPE_NAME_MAP = new HashMap<>() {
        {
            put("error", "err");
        }
    };

    private static final Set<String> GOLANG_BASIC_TYPE = new HashSet<>() {
        {
            add("uint8");
            add("uint16");
            add("uint32");
            add("uint64");
            add("int");
            add("int8");
            add("int16");
            add("int32");
            add("int64");
            add("float32");
            add("float64");
            add("complex64");
            add("complex128");
            add("string");
            add("uint");
            add("uintptr");
            add("byte");
            add("rune");
        }
    };
}
