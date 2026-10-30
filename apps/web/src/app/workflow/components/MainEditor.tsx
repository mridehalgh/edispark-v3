import React, { useState, useEffect } from 'react';
import WorkflowEditor from './WorkflowEditor';
import JsonEditor from './../../../components/json/JsonEditor';
import initialElements from '../data/initialElements';
import { initialJsonData, initialInboundJsonData } from '../data/initialJsonData';
import workflowSchema from '../data/workflowSchema';
import Ajv from 'ajv';
import { Node, Edge } from '@xyflow/react';

const ajv = new Ajv();
const validate = ajv.compile(workflowSchema);

const MainEditor: React.FC = () => {
  const [elements, setElements] = useState<(Node<any> | Edge<any>)[]>(initialElements);
  const [json, setJson] = useState<object>(initialJsonData);
  const [isValid, setIsValid] = useState<boolean>(true);
  const [showJsonEditor, setShowJsonEditor] = useState<boolean>(false);
  const [workflowType, setWorkflowType] = useState<string>('outbound');

  useEffect(() => {
    // @ts-ignore
    setIsValid(validate(json));
  }, [json]);

  const handleJsonChange = (updatedJson: object) => {
    setJson(updatedJson);
  };

  const handleToggleEditor = () => {
    setShowJsonEditor(!showJsonEditor);
  };

  const handleWorkflowTypeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const type = e.target.value;
    setWorkflowType(type);
    setJson(type === 'outbound' ? initialJsonData : initialInboundJsonData);
  };

  return (
      <div>
        <button onClick={handleToggleEditor}>
          {showJsonEditor ? 'Switch to Visual Editor' : 'Switch to JSON Editor'}
        </button>
        <select onChange={handleWorkflowTypeChange} value={workflowType}>
          <option value="outbound">Outbound Workflow</option>
          <option value="inbound">Inbound Workflow</option>
        </select>
        {showJsonEditor ? (
            <JsonEditor data={json} onChange={handleJsonChange} />
        ) : (
            <WorkflowEditor elements={elements} setElements={setElements} />
        )}
        {!isValid && <div style={{ color: 'red' }}>Invalid JSON structure</div>}
      </div>
  );
};

export default MainEditor;
