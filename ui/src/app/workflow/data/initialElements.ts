const initialElements = [
  {
    id: '1',
    type: 'input', // input node
    data: { label: 'Start' },
    position: { x: 250, y: 5 },
  },
  {
    id: '2',
    data: { label: 'Parse EDI Transaction' },
    position: { x: 100, y: 100 },
  },
  {
    id: '3',
    data: { label: 'Batch Transactions' },
    position: { x: 400, y: 100 },
  },
  {
    id: '4',
    data: { label: 'Send to Partner System A' },
    position: { x: 100, y: 200 },
  },
  {
    id: '5',
    data: { label: 'Send to Partner System B' },
    position: { x: 400, y: 200 },
  },
  {
    id: '6',
    type: 'output', // output node
    data: { label: 'End' },
    position: { x: 250, y: 300 },
  },
  // connecting lines
  { id: 'e1-2', source: '1', target: '2', animated: true },
  { id: 'e2-3', source: '2', target: '3' },
  { id: 'e3-4', source: '3', target: '4' },
  { id: 'e3-5', source: '3', target: '5' },
  { id: 'e4-6', source: '4', target: '6' },
  { id: 'e5-6', source: '5', target: '6' },
];

export default initialElements;
