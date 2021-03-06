package analyzer.visitors;

import analyzer.ast.*;

import java.io.PrintWriter;
import java.util.*;


/**
 * Created: 19-02-15
 * Last Changed: 19-11-14
 * Author: Félix Brunet & Doriane Olewicki
 *
 * Description: Ce visiteur explore l'AST et génère un code intermédiaire.
 */

public class LifeVariablesVisitor implements ParserVisitor {

    //le m_writer est un Output_Stream connecter au fichier "result". c'est donc ce qui permet de print dans les fichiers
    //le code généré.
    private /*final*/ PrintWriter m_writer;

    public LifeVariablesVisitor(PrintWriter writer) {
        m_writer = writer;
    }
    public HashMap<String, StepStatus> allSteps = new HashMap<>();

    private int step = 0;
    private HashSet<String> previous_step = new HashSet<>();

    /*
    génère une nouvelle variable temporaire qu'il est possible de print
    À noté qu'il serait possible de rentrer en conflit avec un nom de variable définit dans le programme.
    Par simplicité, dans ce tp, nous ne concidérerons pas cette possibilité, mais il faudrait un générateur de nom de
    variable beaucoup plus robuste dans un vrai compilateur.
     */
    private String genStep() {
        return "_step" + step++;
    }



    @Override
    public Object visit(SimpleNode node, Object data) {
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data)  {
        node.childrenAccept(this, data);
        compute_IN_OUT();
        for (int i = 0; i < step; i++) {
            m_writer.write("===== STEP " + i + " ===== \n" + allSteps.get("_step" + i).toString());
        }
        return null;
    }

    @Override
    public Object visit(ASTDeclaration node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {

        String step = genStep();
        StepStatus s = new StepStatus();
        allSteps.put(step, s);

        for (String e : previous_step) {
            allSteps.get(step).PRED.add(e);
            allSteps.get(e).SUCC.add(step);
        }


        // Update previous step to current
        previous_step = new HashSet<>();
        previous_step.add(step);

        node.childrenAccept(this, previous_step);
        return previous_step;
    }

    /*
    le If Stmt doit vérifier s'il à trois enfants pour savoir s'il s'agit d'un "if-then" ou d'un "if-then-else".
     */
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        //Expression
        node.jjtGetChild(0).jjtAccept(this,data);
        HashSet<String> mem_previous_step = (HashSet<String>) previous_step.clone();
        HashSet<String> if_previous_step;
        if (node.jjtGetNumChildren() == 2) {
            if_previous_step = (HashSet<String>) previous_step.clone(); // if no else
        }
        else {
            if_previous_step = new HashSet<>(); // if there is a else
        }
        for(int i=1; i < node.jjtGetNumChildren(); i++ ){
            //reset previous for each block
            previous_step = (HashSet<String>) mem_previous_step.clone();
            node.jjtGetChild(i).jjtAccept(this,data);
            for (String e : previous_step) {
                if_previous_step.add(e);
            }
        }

        previous_step = (HashSet<String>) if_previous_step.clone();
        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        Object visit = node.jjtGetChild(0).jjtAccept(this, data);
        HashSet<String> mem_previous_step = (HashSet<String>) previous_step.clone();
        HashSet<String> whilePrevStep = new HashSet<>();
        String lastChild = "";

        for(int i = 1; i < node.jjtGetNumChildren(); i++ ) {
            node.jjtGetChild(i).jjtAccept(this, data);
            for (String e : mem_previous_step) {
                whilePrevStep.add(e);
            }
            lastChild = "_step" + (step - 1);
        }

        for (String e : whilePrevStep) {
            allSteps.get(lastChild).SUCC.add(e);
            allSteps.get(e).PRED.add(lastChild);
        }

        previous_step = (HashSet<String>) whilePrevStep.clone();
        return visit;
    }


    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        node.childrenAccept(this, data);
        allSteps.get("_step" + (step - 1)).DEF.add(((ASTIdentifier) node.jjtGetChild(0)).getValue());
        getRefs(node, data);
        return null;
    }


    //Il n'y a probablement rien à faire ici
    @Override
    public Object visit(ASTExpr node, Object data){
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        getRefs(node, data);
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        getRefs(node, data);
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        Object visit = node.jjtGetChild(0).jjtAccept(this, data);
        if (visit != null) {
            allSteps.get("_step" + (step - 1)).REF.add((String) visit);
        }
        return visit;
    }

    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        getRefs(node, data);
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        getRefs(node, data);
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTNotExpr node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTGenValue node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTBoolValue node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        node.childrenAccept(this, data);
        return "" +node.getValue();
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }



    @Override
    public Object visit(ASTSwitchStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTCaseStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTDefaultStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    } 

    //utile surtout pour envoyé de l'informations au enfant des expressions logiques.
    static private class StepStatus {
        public HashSet<String> REF = new HashSet<String>();
        public HashSet<String> DEF = new HashSet<String>();
        public HashSet<String> IN  = new HashSet<String>();
        public HashSet<String> OUT = new HashSet<String>();

        public HashSet<String> SUCC  = new HashSet<String>();
        public HashSet<String> PRED  = new HashSet<String>();

        public String toString() {
            String buff = "";
            buff += "REF : " + set_ordered(REF) +"\n";
            buff += "DEF : " + set_ordered(DEF) +"\n";
            buff += "IN  : " + set_ordered(IN) +"\n";
            buff += "OUT : " + set_ordered(OUT) +"\n";

            buff += "SUCC: " + set_ordered(SUCC) +"\n";
            buff += "PRED: " + set_ordered(PRED) +"\n";
            buff += "\n";
            return buff;
        }

        public String set_ordered(HashSet<String> s) {
            List<String> list = new ArrayList<String>(s);
            Collections.sort(list);

            return list.toString();
        }
    }

    private void compute_IN_OUT() {
        Stack<StepStatus> workList = new Stack<>();
        workList.push(allSteps.get("_step" + (step - 1)));
        while (!workList.empty()) {
            StepStatus node = workList.pop();
            for (String succ : node.SUCC) {
                StepStatus succNode = allSteps.get(succ);
                node.OUT.addAll(succNode.IN);
            }
            HashSet<String> oldIn = (HashSet<String>) node.IN.clone();
            node.IN.addAll(node.OUT);
            for (String def : node.DEF) {
                node.IN.remove(def);
            }
            node.IN.addAll(node.REF);

            if (!node.IN.equals(oldIn)) {
                for (String pred : node.PRED) {
                    workList.push(allSteps.get(pred));
                }
            }
        }
    }

    /*
     * Fonctions utilitaires
     * */

    private void getRefs(Node node, Object data) {
        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            String ref = (String)node.jjtGetChild(i).jjtAccept(this, data);
            if (ref != null) {
                allSteps.get("_step" + (step - 1)).REF.add(ref);
            }
        }
    }
}
