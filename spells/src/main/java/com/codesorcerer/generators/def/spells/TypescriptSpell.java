package com.codesorcerer.generators.def.spells;

import com.codesorcerer.Collector;
import com.codesorcerer.abstracts.AbstractSpell;
import com.codesorcerer.abstracts.Result;
import com.codesorcerer.generators.def.BeanDefInfo;
import com.codesorcerer.generators.def.BeanDefInfo.BeanDefFieldInfo;
import com.codesorcerer.targets.BBBTypescript;
import com.codesorcerer.targets.TypescriptMapping;
import com.codesorcerer.typescript.TSUtils;
import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class TypescriptSpell extends AbstractSpell<BBBTypescript, BeanDefInfo, TypescriptSpell.Out> {

    public static class Out {
        BeanDefInfo ic;
        String ts;
        Set<TypeMirror> mappings;
    }

    private Set<TypeMirror> referenced = Sets.newHashSet();


    @Override
    public int getRunOrder() {
        return 1000;
    }


    @Override
    public void processingOver(Collection<Result> results) throws Exception {
    }


    @Override
    public void modify(Result<AbstractSpell<BBBTypescript, BeanDefInfo, Out>, BeanDefInfo, Out> result, Collection<Result> results) throws Exception {

    }

    @Override
    public void write(Result<AbstractSpell<BBBTypescript, BeanDefInfo, Out>, BeanDefInfo, Out> result) throws Exception {
        BeanDefInfo ic = result.input;
        File dir = TSUtils.getDirToWriteInto(ic.pkg);
        //System.out.println("res.out " + result.output);
        FileUtils.write(new File(dir, ic.immutableClassName + ".ts"), result.output.ts, Charset.defaultCharset());
    }


    @Override
    public void build(Result<AbstractSpell<BBBTypescript, BeanDefInfo, Out>, BeanDefInfo, Out> result) throws Exception {

        BeanDefInfo ic = result.input;
        Set<TypescriptMapping> mappings = TSUtils.getAllMappings(ic.typeElement);

        StringBuilder sb = new StringBuilder();
        sb.append("import {Type, Expose} from 'class-transformer';\n");
        sb.append("*IMPORTS*");

        buildBuilder(ic, sb, mappings);
        buildClass(ic, sb, mappings);

        //Register
        ic.beanDefFieldInfos.forEach(i -> addReferences(i.getter));
        String imports = TSUtils.convertToImportStatements(ic.pkg, referenced, mappings, processingEnvironment);
        String x = sb.toString().replace("*IMPORTS*", imports);

        Out out = new Out();
        out.ts = x;
        out.ic = ic;
        out.mappings = referenced;

        //TODO:
        referenced.clear();

        Collector.COLLECTOR.putAll("mappings", TSUtils.getAllMappings(ic.typeElement));
//        Collector.COLLECTOR.put("packages", ic.pkg);

        result.output = out;
    }

    private void buildClass(BeanDefInfo ic, StringBuilder sb, Set<TypescriptMapping> mappings) {
        sb.append("export class " + ic.immutableClassName + " {\n");


        buildFields2(ic, sb, mappings);
        buildStaticStarter(ic, sb, mappings);
        buildPrivateConstructor2(ic, sb, mappings);
        buildGetters(ic, sb, mappings);
        buildWith(ic, sb, mappings);
        sb.append("}");
    }


    private void buildBuilder(BeanDefInfo ic, StringBuilder sb, Set<TypescriptMapping> mappings) {
        sb.append("export class " + ic.immutableClassName + "Builder ");
        buildImplements(ic, sb);
        sb.append(" {\n");

        buildFields(ic, sb, mappings);
        buildPrivateConstructor(sb);
        buildSetters(ic, sb, mappings);
        buildBuild(ic, sb);

        sb.append("}");

        buildRequiresInterfaces(ic, sb, mappings);
        buildNullableInterface(ic, sb, mappings);
    }

    private void buildImplements(BeanDefInfo ic, StringBuilder sb) {
        sb.append("implements ");
        for (int x = 0; x < ic.nonNullBeanDefFieldInfos.size(); x++) {
            BeanDefFieldInfo i = ic.nonNullBeanDefFieldInfos.get(x);
            sb.append(ic.immutableClassName + "Requires" + i.nameUpper + ", ");
        }
        sb.append(ic.immutableClassName + "Nullable");
    }

    private void buildBuild(BeanDefInfo ic, StringBuilder sb) {
        String allParams = ic.beanDefFieldInfos.stream()
                .map(i -> "this._" + i.nameMangled)
                .collect(Collectors.joining(", "));

        sb.append("build() : " + ic.immutableClassName + " {\n");
        sb.append(" return new " + ic.immutableClassName + "(" + allParams + ");\n");
        sb.append("}");
        sb.append("\n");
    }

    private void buildGetters(BeanDefInfo ic, StringBuilder sb, Set<TypescriptMapping> mappings) {
        for (int x = 0; x < ic.beanDefFieldInfos.size(); x++) {
            BeanDefFieldInfo i = ic.beanDefFieldInfos.get(x);
            sb.append("public get " + i.nameMangled + "() : " + TSUtils.convertToTypescriptType(i.getter.getReturnType(), mappings, processingEnvironment) + " { return this._" + i.nameMangled + "; }\n");
        }
    }


    private void buildSetters(BeanDefInfo ic, StringBuilder sb, Set<TypescriptMapping> mappings) {
        //NonNull stuff
        if (ic.nonNullBeanDefFieldInfos.size() > 0) {
            for (int x = 0; x < ic.nonNullBeanDefFieldInfos.size() - 1; x++) {
                BeanDefFieldInfo i = ic.nonNullBeanDefFieldInfos.get(x);
                BeanDefFieldInfo ii = ic.nonNullBeanDefFieldInfos.get(x + 1);
                String returnType = ic.immutableClassName + "Requires" + ii.nameUpper;
                setter(sb, i, returnType, mappings);
                sb.append("\n");
            }

            BeanDefFieldInfo i = ic.nonNullBeanDefFieldInfos.get(ic.nonNullBeanDefFieldInfos.size() - 1);
            String returnType = ic.immutableClassName + "Nullable";
            setter(sb, i, returnType, mappings);
            sb.append("\n");
        }

        //Nullable
        for (int x = 0; x < ic.nullableBeanDefFieldInfos.size(); x++) {
            BeanDefFieldInfo i = ic.nullableBeanDefFieldInfos.get(x);
            String returnType = ic.immutableClassName + "Nullable";
            setter(sb, i, returnType, mappings);
            sb.append("\n");
        }
    }

    private void buildWith(BeanDefInfo ic, StringBuilder sb, Set<TypescriptMapping> mappings) {

        for (int x = 0; x < ic.beanDefFieldInfos.size(); x++) {
            BeanDefFieldInfo i = ic.beanDefFieldInfos.get(x);

            //compute the list of parameters, with only one not having 'this.' before
            String allParams = ic.beanDefFieldInfos.stream()
                    .map(p -> {
                        if (p.name.equals(i.name)) {
                            return p.nameMangled;
                        }
                        return "this._" + p.nameMangled;
                    })
                    .collect(Collectors.joining(", "));

            String returnType = ic.immutableClassName + "Nullable";
            sb.append("public with" + i.nameUpper + "(" + i.nameMangled + " : " + TSUtils.convertToTypescriptType(i.getter.getReturnType(), mappings, processingEnvironment) + ") : " + ic.immutableClassName + " {\n");
            sb.append("  return new " + ic.immutableClassName + "(" + allParams + ");\n");
            sb.append("}\n");
            sb.append("\n");
        }
    }

    private void setter(StringBuilder sb, BeanDefFieldInfo i, String returnType, Set<TypescriptMapping> mappings) {
        sb.append("public " + i.nameMangled + "(" + i.nameMangled + " : " + TSUtils.convertToTypescriptType(i.getter.getReturnType(), mappings, processingEnvironment) + ") : " + returnType + " {\n");
        sb.append("  this._" + i.nameMangled + " = " + i.nameMangled + ";\n");
        sb.append("  return this;\n");
        sb.append("}\n");
    }

    private void buildStaticStarter(BeanDefInfo ic, StringBuilder sb, Set<TypescriptMapping> mappings) {
        String retType = "";
        if (!ic.nonNullBeanDefFieldInfos.isEmpty()) {
            BeanDefFieldInfo i = ic.nonNullBeanDefFieldInfos.get(0);
            retType = ic.immutableClassName + "Requires" + i.nameUpper;
        } else {
            retType = ic.immutableClassName + "Nullable";
        }
        sb.append("static build" + ic.immutableClassName + "() : " + retType + " {\n");
        sb.append("  return new " + ic.immutableClassName + "Builder();\n");
        sb.append("}\n");

        if (ic.beanDefFieldInfos.size() <= 3) {
            String allParams1 = ic.beanDefFieldInfos.stream()
                    .map(i -> i.nameMangled + " : " + TSUtils.convertToTypescriptType(i.getter.getReturnType(), mappings, processingEnvironment))
                    .collect(Collectors.joining(", "));

            String allParams2 = ic.beanDefFieldInfos.stream()
                    .map(i -> i.nameMangled)
                    .collect(Collectors.joining(", "));

            sb.append("static new" + ic.immutableClassName + "(" + allParams1 + ") : " + ic.immutableClassName + " {\n");
            sb.append("  return new " + ic.immutableClassName + "(" + allParams2 + ");\n");
            sb.append("}\n");
        }

        sb.append("\n");
    }

    private void buildNullableInterface(BeanDefInfo ic, StringBuilder sb, Set<TypescriptMapping> mappings) {
        sb.append("export interface " + ic.immutableClassName + "Nullable {\n");
        for (int x = 0; x < ic.nullableBeanDefFieldInfos.size(); x++) {
            BeanDefFieldInfo i = ic.nullableBeanDefFieldInfos.get(x);
            sb.append("  " + i.nameMangled + "(" + i.nameMangled + " : " + TSUtils.convertToTypescriptType(i.getter.getReturnType(), mappings, processingEnvironment) + ") : " + ic.immutableClassName + "Nullable;\n");
        }
        sb.append("  build() : " + ic.immutableClassName + ";\n");

        sb.append("}\n");
        sb.append("\n");
    }

    private void buildRequiresInterfaces(BeanDefInfo ic, StringBuilder sb, Set<TypescriptMapping> mappings) {
        if (ic.nonNullBeanDefFieldInfos.size() > 0) {
            for (int x = 0; x < ic.nonNullBeanDefFieldInfos.size() - 1; x++) {
                BeanDefFieldInfo i = ic.nonNullBeanDefFieldInfos.get(x);
                BeanDefFieldInfo ii = ic.nonNullBeanDefFieldInfos.get(x + 1);
                sb.append("export interface " + ic.immutableClassName + "Requires" + i.nameUpper + " {\n");
                sb.append("  " + i.nameMangled + "(" + i.nameMangled + " : " + TSUtils.convertToTypescriptType(i.getter.getReturnType(), mappings, processingEnvironment) + ") : " + ic.immutableClassName + "Requires" + ii.nameUpper + ";\n");
                sb.append("}\n");
            }
            sb.append("\n");

            BeanDefFieldInfo i = ic.nonNullBeanDefFieldInfos.get(ic.nonNullBeanDefFieldInfos.size() - 1);
            sb.append("export interface " + ic.immutableClassName + "Requires" + i.nameUpper + " {\n");
            sb.append("  " + i.nameMangled + "(" + i.nameMangled + " : " + TSUtils.convertToTypescriptType(i.getter.getReturnType(), mappings, processingEnvironment) + ") : " + ic.immutableClassName + "Nullable;\n");
            sb.append("}\n");
        }
    }

    private void buildPrivateConstructor(StringBuilder sb) {
        sb.append("public constructor() {}\n");
        sb.append("\n");
    }

    private void buildPrivateConstructor2(BeanDefInfo ic, StringBuilder sb, Set<TypescriptMapping> mappings) {
        String allParams = ic.beanDefFieldInfos.stream()
                .map(i -> i.nameMangled + "? : " + TSUtils.convertToTypescriptType(i.getter.getReturnType(), mappings, processingEnvironment))
                .collect(Collectors.joining(", "));


        sb.append("public constructor( " + allParams + ") {\n");
        for (BeanDefFieldInfo i : ic.beanDefFieldInfos) {
            sb.append("  this._" + i.nameMangled + " = " + i.nameMangled + ";\n");
        }
        sb.append("}");
        sb.append("\n");

//        sb.append("public constructor() {}");
        sb.append("\n");
    }

    private void buildFields(BeanDefInfo ic, StringBuilder sb, Set<TypescriptMapping> mappings) {
        for (BeanDefFieldInfo i : ic.beanDefFieldInfos) {
            sb.append("  _" + i.nameMangled + ": " + TSUtils.convertToTypescriptType(i.getter.getReturnType(), mappings, processingEnvironment) + ";\n");
        }
        sb.append("\n");
    }

    private void buildFields2(BeanDefInfo ic, StringBuilder sb, Set<TypescriptMapping> mappings) {
        for (BeanDefFieldInfo i : ic.beanDefFieldInfos) {
            String typ = TSUtils.convertToTypescriptType(i.getter.getReturnType(), mappings, processingEnvironment);

            String ann = "";

            //TODO... what other types?
            if (!typ.equals("Array<string>") && !typ.equals("Array<number>") && !typ.equals("Array<boolean>")) {
                if (!typ.equals("string") && !typ.equals("number") && !typ.equals("boolean")) {
                    ann = "@Type(() => " + typ + ")";
                }
            }

            sb.append(ann + "  @Expose({ name: '" + i.nameMangled + "' })" + " private _" + i.nameMangled + ": " + typ + ";\n");
        }
        sb.append("\n");
    }


    private void addReferences(ExecutableElement e) {
        referenced.addAll(TSUtils.getReferences(e));
    }

}