'use client'
import React, {useCallback, useState} from 'react';
import {
  ReactFlow,
  ReactFlowProvider,
  addEdge,
  useNodesState,
  useEdgesState,
  Edge,
  Node,
  Connection,
  MiniMap,
  Controls,
  Background, applyNodeChanges, applyEdgeChanges,
  EdgeChange,
  NodeChange,
} from '@xyflow/react';

interface WorkflowEditorProps {
  elements: (Node<any> | Edge<any>)[];
  setElements: React.Dispatch<React.SetStateAction<(Node<any> | Edge<any>)[]>>;
}

const WorkflowEditor: React.FC<WorkflowEditorProps> = ({elements, setElements}) => {
  const [nodes, setNodes] = useState(elements.filter((el) => el.type === 'node') as Node[]);
  const [edges, setEdges] = useState(elements.filter((el) => el.type === 'edge') as Edge[]);


  const onNodesChange = useCallback(
      (changes: NodeChange<Node>[]) => setNodes((nds) => applyNodeChanges(changes, nds)),
      [],
  );
  const onEdgesChange = useCallback(
      (changes: EdgeChange<Edge>[]) => setEdges((eds) => applyEdgeChanges(changes, eds)),
      [],
  );

  const onConnect = useCallback(
      (params: Edge<any> | Connection) => setEdges((els) => addEdge(params, els)),
      [setEdges]
  );

  return (
      <div style={{ height: 800 }}>
      <ReactFlowProvider>
      <ReactFlow
          nodes={nodes}
          onNodesChange={onNodesChange}
          edges={edges}
          onEdgesChange={onEdgesChange}
      >
          <MiniMap />
          <Controls />
          <Background color="#aaa" gap={16} />
      </ReactFlow>
      </ReactFlowProvider>
      </div>
  );
};

export default WorkflowEditor;
