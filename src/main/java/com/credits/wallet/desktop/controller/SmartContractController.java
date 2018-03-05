package com.credits.wallet.desktop.controller;

import com.credits.wallet.desktop.App;
import com.credits.wallet.desktop.AppState;
import com.credits.wallet.desktop.Utils;
import com.credits.wallet.desktop.struct.ErrorCodeTabRow;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.*;

/**
 * Created by goncharov-eg on 30.01.2018.
 */
public class SmartContractController extends Controller implements Initializable {

    private static Logger LOGGER = LoggerFactory.getLogger(SmartContractController.class);

    private static final String[] KEYWORDS =
        new String[] {"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch",
            "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while"};

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String PAREN_PATTERN = "[()]";
    private static final String BRACE_PATTERN = "[{}]";
    private static final String BRACKET_PATTERN = "[\\[]]";
    private static final String SEMICOLON_PATTERN = ";";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";

    private static final Pattern PATTERN = Pattern.compile(
        "(?<KEYWORD>" + KEYWORD_PATTERN + ")" + "|(?<PAREN>" + PAREN_PATTERN + ")" + "|(?<BRACE>" + BRACE_PATTERN +
            ")" + "|(?<BRACKET>" + BRACKET_PATTERN + ")" + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")" + "|(?<STRING>" +
            STRING_PATTERN + ")" + "|(?<COMMENT>" + COMMENT_PATTERN + ")");

    private CodeArea codeArea;
    private TableView tabErrors;


    @FXML
    private Pane paneCode;

    @FXML
    private TreeView<Label> classTreeView;

    @FXML
    private Button checkButton;

    //@FXML
    //private javafx.scene.control.TextArea taCode;

    @FXML
    private void handleBack() {
        if (AppState.executor != null) {
            AppState.executor.shutdown();
            AppState.executor = null;
        }
        App.showForm("/fxml/form6.fxml", "Wallet");
    }

    @FXML
    private void handleDeploy() {
        char[] characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
        StringBuilder sb = new StringBuilder();
        sb.append("CST");
        Random random = new Random();
        int max = characters.length - 1;
        for (int i = 0; i < 29; i++) {
            sb.append(characters[random.nextInt(max)]);
        }

        String token = sb.toString();

        StringSelection selection = new StringSelection(token);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);

        // Call contract executor
        if (AppState.contractExecutorJava != null) {
            // Parse className
            String className = "SmartContract";

            String javaCode = codeArea.getText().replace("\r", " ").replace("\n", " ").replace("{", " {");

            while (javaCode.contains("  ")) {
                javaCode = javaCode.replace("  ", " ");
            }
            java.util.List<String> javaCodeWords = asList(javaCode.split(" "));
            int ind = javaCodeWords.indexOf("class");
            if (ind >= 0 && ind < javaCodeWords.size() - 1) {
                className = javaCodeWords.get(ind + 1);
            }
            // ---------------
            try {
                String tmpDir = System.getProperty("java.io.tmpdir");
                String tmpFileName = tmpDir + File.separator + className + ".java";
                File tmpFile = new File(tmpFileName);
                FileOutputStream out = new FileOutputStream(tmpFile);
                out.write(codeArea.getText().getBytes());
                out.close();

                CloseableHttpClient client = HttpClients.createDefault();
                HttpPost httpPost = new HttpPost(AppState.contractExecutorJava);
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.addTextBody("address", AppState.account);
                builder.addBinaryBody("java", tmpFile, ContentType.APPLICATION_OCTET_STREAM, className + ".java");

                HttpEntity multipart = builder.build();
                httpPost.setEntity(multipart);

                CloseableHttpResponse response = client.execute(httpPost);

                if (response.getStatusLine().getStatusCode() != 200) {
                    // Show error
                    BufferedReader reader =
                        new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

                    String inputLine;
                    StringBuilder sbResponse = new StringBuilder();

                    while ((inputLine = reader.readLine()) != null) {
                        sbResponse.append(inputLine);
                    }
                    reader.close();

                    JsonElement jelement = new JsonParser().parse(sbResponse.toString());
                    String msgStr = jelement.getAsJsonObject().get("message").getAsString();
                    String errorStr = jelement.getAsJsonObject().get("error").getAsString();
                    String excStr = jelement.getAsJsonObject().get("exception").getAsString();

                    String errorMsg = "";
                    if (msgStr != null) {
                        errorMsg = msgStr;
                    }
                    if (errorStr != null) {
                        errorMsg = errorMsg.trim() + " " + errorStr;
                    }
                    if (excStr != null) {
                        errorMsg = errorMsg.trim() + " " + excStr;
                    }

                    Utils.showError(errorMsg);
                }

                client.close();

                tmpFile.delete();
            } catch (Exception e) {
                e.printStackTrace();
                Utils.showError("Error executing smart contract " + e.toString());
            }
        }
        // ----------------------

        Utils.showInfo("Token\n\n" + token + "\n\nhas generated and copied to clipboard");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initialize(URL location, ResourceBundle resources) {
        if (AppState.executor != null) {
            AppState.executor.shutdown();
        }
        AppState.executor = Executors.newSingleThreadExecutor();
        codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.richChanges()
            .filter(ch -> !ch.getInserted().equals(ch.getRemoved())) // XXX
            .successionEnds(Duration.ofMillis(500))
            .supplyTask(this::computeHighlightingAsync)
            .awaitLatest(codeArea.richChanges())
            .filterMap(t -> {
                if (t.isSuccess()) {
                    return Optional.of(t.get());
                } else {
                    t.getFailure().printStackTrace();
                    return Optional.empty();
                }
            })
            .subscribe(this::applyHighlighting);

        codeArea.setPrefHeight(paneCode.getPrefHeight());
        codeArea.setPrefWidth(paneCode.getPrefWidth());
        paneCode.getChildren().add(codeArea);

        tabErrors = new TableView();
        tabErrors.setPrefHeight(paneCode.getPrefHeight() * 0.3);
        tabErrors.setPrefWidth(paneCode.getPrefWidth());

        TableColumn tabErrorsColLine = new TableColumn();
        tabErrorsColLine.setText("Line");
        tabErrorsColLine.setPrefWidth(paneCode.getPrefWidth() * 0.1);
        TableColumn tabErrorsColText = new TableColumn();
        tabErrorsColText.setText("Error");
        tabErrorsColText.setPrefWidth(paneCode.getPrefWidth() * 0.9);
        tabErrors.getColumns().add(tabErrorsColLine);
        tabErrors.getColumns().add(tabErrorsColText);

        TableColumn[] tableColumns = new TableColumn[tabErrors.getColumns().size()];
        for (int i = 0; i < tabErrors.getColumns().size(); i++) {
            tableColumns[i] = (TableColumn) tabErrors.getColumns().get(i);
        }
        tableColumns[0].setCellValueFactory(new PropertyValueFactory<ErrorCodeTabRow, String>("line"));
        tableColumns[1].setCellValueFactory(new PropertyValueFactory<ErrorCodeTabRow, String>("text"));

        tabErrors.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                ErrorCodeTabRow tabRow = (ErrorCodeTabRow) tabErrors.getSelectionModel().getSelectedItem();
                if (tabRow != null) {
                    positionCodeAreaToLine(Integer.valueOf(tabRow.getLine()));
                }
            }
        });

    }

    @FXML
    private void panelCodeKeyReleased() {
        refreshClassMembersTree();
    }

    private void refreshClassMembersTree() {

        this.classTreeView.setRoot(null);

        String sourceCode = codeArea.getText();

        CompilationUnit compilationUnit = createCompilationUnit(sourceCode);

        List typeList = compilationUnit.types();

        if (typeList.size() != 1) {
            return;
        }

        TypeDeclaration typeDeclaration = (TypeDeclaration) typeList.get(0);

        String className = (typeDeclaration).getName().getFullyQualifiedName();

        Label labelRoot = new Label(className);

        TreeItem<Label> treeRoot = new TreeItem<>(labelRoot);

        ASTNode root = compilationUnit.getRoot();

        root.accept(new ASTVisitor() {

            @Override
            public boolean visit(FieldDeclaration node) {
                return true;
            }

            @Override
            public void endVisit(FieldDeclaration node) {
                Label label = new Label(node.toString());

                label.setOnMousePressed(event -> {
                    if (event.isPrimaryButtonDown()) {
                        positionCodeAreaToLine(compilationUnit.getLineNumber(node.getStartPosition()));
                    }
                });

                TreeItem<Label> treeItem = new TreeItem<>();
                treeItem.setValue(label);

                treeRoot.getChildren().add(treeItem);
            }

        });

        root.accept(new ASTVisitor() {

            @Override
            public boolean visit(MethodDeclaration node) {
                return true;
            }

            @Override
            public void endVisit(MethodDeclaration node) {
                node.setBody(null);
                Label label = new Label(node.toString());
                label.setOnMousePressed(event -> {
                    if (event.isPrimaryButtonDown()) {
                        positionCodeAreaToLine(compilationUnit.getLineNumber(node.getStartPosition()));
                    }
                });

                TreeItem<Label> treeItem = new TreeItem<>();
                treeItem.setValue(label);

                treeRoot.getChildren().add(treeItem);
            }

        });

        treeRoot.setExpanded(true);
        this.classTreeView.setRoot(treeRoot);
    }

    @FXML
    @SuppressWarnings("unchecked")
    private void checkButtonAction() {
        String sourceCode = codeArea.getText();

        CompilationUnit compilationUnit = createCompilationUnit(sourceCode);

        IProblem[] problemArr = compilationUnit.getProblems();

        if (problemArr.length > 0) {
            tabErrors.getItems().clear();

            for (IProblem p : problemArr) {
                ErrorCodeTabRow tr = new ErrorCodeTabRow();
                tr.setLine(Integer.toString(p.getSourceLineNumber()));
                tr.setText(p.getMessage());
                tabErrors.getItems().add(tr);
            }

            codeArea.setPrefHeight(paneCode.getPrefHeight() * 0.7);
            paneCode.getChildren().clear();
            paneCode.getChildren().add(codeArea);
            paneCode.getChildren().add(tabErrors);
            paneCode.getChildren().get(1).setLayoutY(paneCode.getPrefHeight() * 0.7);
        } else {
            codeArea.setPrefHeight(paneCode.getPrefHeight());
            paneCode.getChildren().clear();
            paneCode.getChildren().add(codeArea);
        }
    }

    private CompilationUnit createCompilationUnit(String sourceCode) {
        ASTParser parser = ASTParser.newParser(AST.JLS9);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(sourceCode.toCharArray());
        parser.setResolveBindings(true);

        return (CompilationUnit) parser.createAST(null);
    }


    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass = matcher.group("KEYWORD") != null ? "keyword" : matcher.group("PAREN") != null ? "paren"
                : matcher.group("BRACE") != null ? "brace" : matcher.group("BRACKET") != null ? "bracket"
                    : matcher.group("SEMICOLON") != null ? "semicolon" : matcher.group("STRING") != null ? "string"
                        : matcher.group("COMMENT") != null ? "comment" : null; /* never happens */
            assert styleClass != null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private void applyHighlighting(StyleSpans<Collection<String>> highlighting) {
        codeArea.setStyleSpans(0, highlighting);
    }

    private Task<StyleSpans<Collection<String>>> computeHighlightingAsync() {
        String text = codeArea.getText();
        Task<StyleSpans<Collection<String>>> task = new Task<StyleSpans<Collection<String>>>() {
            @Override
            protected StyleSpans<Collection<String>> call() throws Exception {
                return computeHighlighting(text);
            }
        };
        AppState.executor.execute(task);
        return task;
    }

    private void positionCodeAreaToLine(int line) {
        char[] text = codeArea.getText().toCharArray();
        int pos = 0;
        int curLine = 1;
        while (pos < text.length) {
            if (line <= curLine) {
                break;
            }
            if (text[pos] == '\n') {
                curLine++;
            }
            pos++;
        }
        codeArea.displaceCaret(pos);
        codeArea.showParagraphAtTop(Math.max(0, line - 5));
        codeArea.requestFocus();
    }
}