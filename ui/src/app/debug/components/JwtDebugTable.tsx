import React from 'react';

interface JWT {
  payload: Record<string, unknown>;
}

interface JwtTableProps {
  token?: JWT;
}

const JwtTable: React.FC<JwtTableProps> = ({ token }) => {

  return (
      <div>
        {token?.payload ? (
            <table className="table-auto border-collapse border border-gray-400">
              <thead>
              <tr>
                <th className="border border-gray-300 px-4 py-2">Key</th>
                <th className="border border-gray-300 px-4 py-2">Value</th>
              </tr>
              </thead>
              <tbody>
              {Object.entries(token.payload).map(([key, value]) => (
                  <tr key={key}>
                    <td className="border border-gray-300 px-4 py-2">{key}</td>
                    <td className="border border-gray-300 px-4 py-2">{JSON.stringify(value)}</td>
                  </tr>
              ))}
              </tbody>
            </table>
        ) : (
            <p>No valid token provided</p>
        )}
      </div>
  );
};

export default JwtTable;
