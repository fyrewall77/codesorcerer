package com.beautifulbeanbuilder.generators.usecase.generator;

import com.beautifulbeanbuilder.generators.usecase.UsecaseInfo;
import com.beautifulbeanbuilder.processor.AbstractGenerator;
import com.beautifulbeanbuilder.processor.AbstractJavaGenerator;
import com.central1.leanannotations.LeanUsecase;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

public class UsecaseControllerGenerator extends AbstractGenerator<LeanUsecase, UsecaseInfo, String>
{
	public static final String DIR = "target/generated-sources/annotations/";

	@Override
	public void processingOver(Collection<String> objects) {
	}

	@Override
	public void write(UsecaseInfo ic, String objectToWrite, ProcessingEnvironment processingEnv) throws IOException
	{
		String fileName = ic.typeElement.getSimpleName().toString().replace( "Usecase", "" ) + "RestController.java";
		File file = new File( DIR + ic.getControllerPackage().replaceAll( "\\.", "/" ), fileName );
		System.out.println("Writing out object " + file.getAbsolutePath() );
		FileUtils.write(file, objectToWrite, Charset.defaultCharset());
	}

	@Override
	public String build(UsecaseInfo ic, Map<AbstractJavaGenerator, Object> generatorBuilderMap, ProcessingEnvironment processingEnv ) throws IOException {

		String usecaseName = ic.typeElement.getSimpleName().toString();
		String fullUsecaseName = ( (Symbol.ClassSymbol) ic.typeElement ).className();

		String controllerName = usecaseName + "RestController";

		// Loop through the exposed method to get Mapper needed
		Map<ExecutableElement, String> readListMethods = Maps.newHashMap();
		Map<ExecutableElement, String> readSingleMethods = Maps.newHashMap();
		List<ExecutableElement> nonReadMethods = Lists.newArrayList();
		for (ExecutableElement e : ic.getAllMethodsExposed()) {
			TypeMirror returnType = e.getReturnType();

			// Make sure returnType is Observable
			//TypeMirror obType = elementUtils.getTypeElement("io.reactivex.Observable").asType();
			//if ( typeUilts.isAssignable( returnType, obType ) && TypeKind.DECLARED.equals( returnType.getKind() ) )
			String returnTypeName = ( (Type.ClassType) returnType ).asElement().getQualifiedName().toString();
			if( returnTypeName.toString().indexOf( "Observable" ) != -1 )
			{
				TypeMirror obParamType = ( (DeclaredType) returnType ).getTypeArguments().get( 0 );
				String obParamTypeName = ( (Type.ClassType) obParamType ).asElement().getQualifiedName().toString();
				//TypeMirror listType = elementUtils.getTypeElement("java.util.List").asType();
				//if( typeUilts.isAssignable( obParamType, listType ) && TypeKind.DECLARED.equals( obParamType.getKind() ))
				if ( TypeKind.DECLARED.equals( obParamType.getKind() ) && obParamTypeName.indexOf( "List" ) != -1 )
				{
					//It is Observable of list
					TypeMirror listParamType = ( (DeclaredType) obParamType ).getTypeArguments().get( 0 );
					String listParamTypeName = ( (Type.ClassType) listParamType ).asElement().getQualifiedName().toString();
					//Get the detailed object type
					String realEntityName =
							listParamTypeName.substring( listParamTypeName.lastIndexOf( '.' ) + 1 ).replaceAll( "Ref", "" );
					readListMethods.put( e, realEntityName );
				}
				else
				{
					//Is it possible to have a Observable<Entity>?
					String realEntityName =
							obParamTypeName.substring( obParamTypeName.lastIndexOf( '.' ) + 1 ).replaceAll( "Ref", "" );
					readSingleMethods.put( e, realEntityName );
				}
			}
			else
			{
				nonReadMethods.add( e );
			}

		}

		final StringBuilder sb = new StringBuilder();

		sb.append( "package " ).append( ic.getControllerPackage() ).append( ";\n" );
		sb.append( "\n" );
		sb.append( "import " ).append( fullUsecaseName ).append( ";\n" );
		sb.append( "import com.central1.lean.entities.*;\n" );
		sb.append( "import com.central1.lean.mapping.Mapper;\n");
		sb.append( "import com.central1.leanannotations.LeanEntryPoint;\n" );

		sb.append( "import io.reactivex.Observable;\n");
		sb.append( "import org.springframework.web.bind.annotation.*;\n");
		sb.append( "import org.springframework.messaging.handler.annotation.DestinationVariable;\n");
		sb.append( "import org.springframework.messaging.simp.annotation.SubscribeMapping;\n");

		sb.append( "import java.util.List;\n" );
		sb.append( "import java.util.Arrays;\n" );
		sb.append( "\n" );

		sb.append( "@LeanEntryPoint\n" );
		sb.append( "@RestController\n" );
		sb.append( "public class " ).append( controllerName ).append( " {\n" );
		sb.append( "	private final ").append( usecaseName ).append( " usecase;\n" );

		Set<String> entities = new HashSet<>( readListMethods.values() );
		entities.addAll( readSingleMethods.values() );
		Map<String, String> eMappers = Maps.newHashMap();
		entities.forEach( e ->
				eMappers.put( e.substring(0, 1).toLowerCase() + e.substring(1) + "Mapper",  "Mapper<" + e + ", " + e + "Ref>" )
		);

		eMappers.entrySet().forEach( entry ->
				sb.append( "	private final ").append( entry.getValue() ).append(" " ).append( entry.getKey() ).append( ";\n" )
		);

		sb.append( "\n" );
		sb.append( "	public ").append( controllerName ).append( "(" );
		eMappers.entrySet().forEach( entry ->
				sb.append( "		" ).append( entry.getValue() ).append(" " ).append( entry.getKey() ).append( ", \n" )
		);
		sb.append( usecaseName ).append( " usecase )");
		sb.append( "{\n");
		sb.append("		this.usecase = usecase;\n" );
		eMappers.keySet().forEach( key ->
				sb.append("		this." ).append( key ).append( "= " ).append( key ).append( ";\n" )
		);
		sb.append( "	}\n" );
		sb.append("\n");

		Types typeUilts = processingEnv.getTypeUtils();
		Elements elementUtils = processingEnv.getElementUtils();

		readListMethods.entrySet().forEach( entry -> {
			String realReturnType = "Observable<List<" + entry.getValue() + ">>";
			String methodName = entry.getKey().getSimpleName().toString();
			sb.append( "	@SubscribeMapping( value = \"/" ).append( methodName ).append( "\")\n" );
			sb.append( "	public " ).append( realReturnType ).append(" ").append( methodName );
			handleParams( entry.getKey(), entry.getValue(), sb, true  );
			sb.append( "	}\n" );
			sb.append( "\n" );
		} );

		readSingleMethods.entrySet().forEach( entry -> {
			String realReturnType = "Observable<" + entry.getValue() + ">";
			String methodName = entry.getKey().getSimpleName().toString();
			sb.append( "	@SubscribeMapping( value = \"/" ).append( methodName ).append( "\")\n" );
			sb.append( "	public " ).append( realReturnType ).append(" ").append( methodName );
			handleParams( entry.getKey(), entry.getValue(), sb, false );
			sb.append( "	}\n" );
			sb.append( "\n" );
		});

		nonReadMethods.forEach( e -> {
			//TODO: may need to convert parameters
			sb.append( "\n" );
			sb.append( "	@RequestMapping( value = \"/" ).append( e.getSimpleName() ).append( "\", method = POST)\n" );
			sb.append( "	public Single<WriteOperationResult> " ).append( e.getSimpleName() ).append( "() {\n" );
			sb.append( "		return usecase." ).append( e.getSimpleName() ).append( ";\n" );
			sb.append( "	}\n" );
			sb.append( "\n" );
		} );

		sb.append( "	private <T, R extends EntityRef<T>> Observable<List<T>> getListObservable( Observable<List<R>> refList, Mapper<T, R> mapper )\n" );
		sb.append( "{\n" );
		sb.append( "		return refList.switchMap( refs -> {\n" );
		sb.append( "			final Iterable<Observable<T>> iterable = refs.stream().map( mapper::getEntity )::iterator;\n");
		sb.append( "			return Observable.combineLatest( iterable, arr -> Arrays.asList( (T[]) arr ) );\n" );
		sb.append( "		} );\n" );
		sb.append( "	}\n");

		sb.append("}\n" );

		return sb.toString();
	}

	private void handleParams( ExecutableElement e, String entityName, StringBuilder sb, boolean isList )
	{
		sb.append( "(" );

		List<String> requestParams = Lists.newArrayList();
		List<String> usecaseParams = Lists.newArrayList();
		if ( e.getParameters().size() > 0 )
		{
			e.getParameters().forEach( paramElement -> {
				String paraType = ( (Type.ClassType) paramElement.asType() ).asElement().getQualifiedName().toString();
				String paraName = paramElement.getSimpleName().toString();
				requestParams.add( "@DestinationVariable( \"" + paraName + "\" ) " + paraType.substring( paraType.lastIndexOf( '.' ) + 1 ) + " " + paraName );
				usecaseParams.add( paraName );
			}  );
		}
		sb.append( StringUtils.join( requestParams, ", ") );
		sb.append( " ) {\n" );

		String usecaseCall = "usecase." + e.getSimpleName().toString() + "(" + StringUtils.join( usecaseParams, ", ") + ")";
		String mapper = entityName.substring(0, 1).toLowerCase() + entityName.substring(1) + "Mapper";
		if ( isList )
		{
			sb.append( "		return getListObservable( " ).append( usecaseCall ).append( ", " ).append( mapper ).append( " );\n" );
		}
		else
		{
			sb.append( "		return " ).append( mapper ).append( ".getEntity( " ).append( usecaseCall ).append( " );\n" );
		}
	}
}
