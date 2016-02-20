package org.elkoserver.server.context;

abstract class CheckpointTask {
    private CheckpointTask() {
    }

    abstract void execute(Contextor contextor);

    static CheckpointTask makeDeleteTask(String ref) {
        return new DeleteTask(ref);
    }

    static CheckpointTask makeWriteTask(String ref, BasicObject obj) {
        return new WriteTask(ref, obj);
    }

    static private class DeleteTask extends CheckpointTask {
        private String myRef;
        DeleteTask(String ref) {
            myRef = ref;
        }
        void execute(Contextor contextor) {
            contextor.writeObjectDelete(myRef);
        }
    }

    static private class WriteTask extends CheckpointTask {
        private String myRef;
        private BasicObject myObj;
        WriteTask(String ref, BasicObject obj) {
            myRef = ref;
            myObj = obj;
        }
        void execute(Contextor contextor) {
            contextor.writeObjectState(myRef, myObj);
        }
    }
}
